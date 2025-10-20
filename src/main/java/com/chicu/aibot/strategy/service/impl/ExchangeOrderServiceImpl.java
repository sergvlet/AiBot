package com.chicu.aibot.strategy.service.impl;

import com.chicu.aibot.exchange.client.ExchangeClient;
import com.chicu.aibot.exchange.client.ExchangeClientFactory;
import com.chicu.aibot.exchange.enums.OrderSide;
import com.chicu.aibot.exchange.model.OrderInfo;
import com.chicu.aibot.exchange.service.ExchangeSettingsService;
import com.chicu.aibot.strategy.model.Order;
import com.chicu.aibot.strategy.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeOrderServiceImpl implements OrderService {

    private final ExchangeClientFactory clientFactory;
    private final ExchangeSettingsService settingsService;
    private final OrderExecutionService executionService; // вынесена логика placeLimit/placeMarket

    // Анти-дупы для MARKET и анти-спам
    private final java.util.Map<String, Long> lastMarketAttemptTs = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long MARKET_COOLDOWN_MS = 30_000; // 30 секунд

    private String normalizeStatus(String raw) {
        if (raw == null) return "";
        return raw.trim().toUpperCase(Locale.ROOT).replace(" ", "").replace("_", "");
    }

    @Override
    public Order placeLimit(Long chatId, String symbol, Order.Side side, double price, double quantity) {
        return executionService.placeLimit(chatId, symbol, side, price, quantity);
    }

    private boolean hasAnyOpenOrders(Long chatId, String symbol) {
        try {
            var settings = settingsService.getOrCreate(chatId);
            var keys     = settingsService.getApiKey(chatId);
            ExchangeClient client = clientFactory.getClient(settings.getExchange());
            List<OrderInfo> open = client.fetchOpenOrders(
                    keys.getPublicKey(), keys.getSecretKey(), settings.getNetwork(), symbol
            );
            return open != null && !open.isEmpty();
        } catch (Exception e) {
            log.warn("hasAnyOpenOrders: ошибка запроса открытых ордеров: {}", e.getMessage());
            // На ошибке лучше не блокировать — возвращаем false
            return false;
        }
    }

    private boolean marketCooldownHit(Long chatId, String symbol, Order.Side side) {
        String key = chatId + ":" + symbol + ":" + side;
        long now = System.currentTimeMillis();
        Long prev = lastMarketAttemptTs.get(key);
        if (prev != null && now - prev < MARKET_COOLDOWN_MS) return true;
        lastMarketAttemptTs.put(key, now);
        return false;
    }

    @Override
    public Order placeMarket(Long chatId, String symbol, Order.Side side, double quantity) {
        // Анти-спам кулдаун
        if (marketCooldownHit(chatId, symbol, side)) {
            log.info("⏳ MARKET cooldown: {} {} qty={} — пропускаем повтор", symbol, side, quantity);
            return new Order("SKIPPED-COOLDOWN", symbol, side, 0.0, quantity, false, false, false);
        }

        // Если уже есть открытые ордера по символу — не стреляем рынком
        if (hasAnyOpenOrders(chatId, symbol)) {
            log.info("⛔️ MARKET заблокирован: есть открытые ордера по {}", symbol);
            return new Order("SKIPPED-OPEN-ORDERS", symbol, side, 0.0, quantity, false, false, false);
        }

        return executionService.placeMarket(chatId, symbol, side, quantity);
    }

    @Override
    public void cancel(Long chatId, Order order) {
        if (order == null) return;

        log.info("Отмена ордера → id={}, symbol={}", order.getId(), order.getSymbol());

        // уже отменён/закрыт — ничего не делаем
        if (order.isCancelled() || order.isClosed()) {
            log.debug("Отмена: уже отменён/закрыт id={}", order.getId());
            return;
        }

        // Локальные REJECTED/пустые id — считаем отменёнными
        if (order.getId() == null || order.getId().startsWith("REJECTED-") || order.getId().startsWith("SKIPPED-")) {
            order.setCancelled(true);
            log.info("Отмена: локальный REJECTED/SKIPPED или пустой id → {}", order.getId());
            return;
        }

        try {
            var settings = settingsService.getOrCreate(chatId);
            var keys     = settingsService.getApiKey(chatId);
            ExchangeClient client = clientFactory.getClient(settings.getExchange());

            String id = order.getId();

            client.cancelOrder(
                    String.valueOf(settings.getExchange()),
                    order.getSymbol(),
                    keys.getPublicKey(),
                    keys.getSecretKey(),
                    settings.getNetwork(),
                    id,
                    null // clientOrderId (если у тебя есть — подставь здесь)
            );

            order.setCancelled(true);
            log.info("Ордер отменён: id={}, symbol={}", id, order.getSymbol());
        } catch (Exception e) {
            String msg = String.valueOf(e.getMessage());
            if (msg.contains("order does not exist") || msg.contains("Unknown order")) {
                order.setCancelled(true);
                log.info("Биржа вернула, что ордера нет (treat as canceled): {}", order.getId());
            } else {
                log.warn("Ошибка отмены ордера {}: {}", order.getId(), e.getMessage());
            }
        }
    }

    @Override
    public void closePosition(Long chatId, Order order) {
        if (order == null) return;

        log.info("Закрыть позицию → {}", order);

        if (order.getVolume() <= 0.0) {
            log.warn("Пропуск закрытия: volume=0");
            return;
        }
        Order.Side opposite = (order.getSide() == Order.Side.BUY) ? Order.Side.SELL : Order.Side.BUY;

        try {
            // делегируем в executionService, который учтёт фильтры/баланс
            Order closed = executionService.placeMarket(chatId, order.getSymbol(), opposite, order.getVolume());
            closed.setClosed(true);
            order.setClosed(true);
            log.info("Позиция закрыта MARKET: {} {} qty={}", order.getSymbol(), opposite, order.getVolume());
        } catch (Exception e) {
            log.error("Ошибка MARKET-закрытия позиции {}: {}", order.getSymbol(), e.getMessage(), e);
        }
    }

    @Override
    public List<Order> loadActiveOrders(Long chatId, String symbol) {
        var settings = settingsService.getOrCreate(chatId);
        var keys     = settingsService.getApiKey(chatId);
        ExchangeClient client = clientFactory.getClient(settings.getExchange());

        List<Order> result = new ArrayList<>();
        try {
            List<OrderInfo> openOrders = client.fetchOpenOrders(
                    keys.getPublicKey(),
                    keys.getSecretKey(),
                    settings.getNetwork(),
                    symbol
            );

            for (OrderInfo oi : openOrders) {
                Order.Side side = (oi.getSide() == OrderSide.BUY)
                        ? Order.Side.BUY
                        : Order.Side.SELL;

                double price     = oi.getPrice() == null ? 0.0 : oi.getPrice().doubleValue();
                double origQty   = oi.getOrigQty() == null ? 0.0 : oi.getOrigQty().doubleValue();
                double executed  = oi.getExecutedQty() == null ? 0.0 : oi.getExecutedQty().doubleValue();

                String statusNorm = normalizeStatus(oi.getStatus());
                boolean filled    = "FILLED".equals(statusNorm) || (executed >= origQty && origQty > 0.0);
                boolean canceled  = "CANCELED".equals(statusNorm) || "CANCELLED".equals(statusNorm)
                                    || "EXPIRED".equals(statusNorm) || "REJECTED".equals(statusNorm);

                if (!canceled) {
                    result.add(new Order(
                            oi.getOrderId(),
                            oi.getSymbol(),
                            side,
                            price,
                            origQty,   // объём ордера — исходный объём
                            filled,
                            false,
                            false
                    ));
                }
            }
        } catch (Exception e) {
            log.warn("Ошибка загрузки активных ордеров для {}: {}", symbol, e.getMessage());
        }
        return result;
    }

    @Override
    public void refreshOrderStatuses(Long chatId, String symbol, List<Order> cache) {
        if (cache == null) return;
        if (cache.isEmpty()) {
            // Гидратим открытые ордера из биржи в пустой кэш
            List<Order> open = loadActiveOrders(chatId, symbol);
            if (open != null && !open.isEmpty()) {
                cache.addAll(open);
            } else {
                return; // нечего обновлять
            }
        }

        var settings = settingsService.getOrCreate(chatId);
        var keys     = settingsService.getApiKey(chatId);
        ExchangeClient client = clientFactory.getClient(settings.getExchange());

        for (Order o : cache) {
            if (o.isCancelled() || o.isClosed()) continue;
            String id = o.getId();
            if (id == null) continue;

            // локальные псевдо-ордера не трогаем
            if (id.startsWith("REJECTED-") || id.startsWith("SKIPPED-")) {
                o.setCancelled(true);
                log.debug("refresh: локальный псевдо-ордер {} помечен отменённым", id);
                continue;
            }

            try {
                var opt = client.fetchOrder(
                        keys.getPublicKey(),
                        keys.getSecretKey(),
                        settings.getNetwork(),
                        symbol,
                        id
                );

                if (opt.isEmpty()) {
                    o.setCancelled(true);
                    log.info("refresh: биржа не вернула ордер (treat as canceled) id={}", id);
                    continue;
                }

                OrderInfo st = opt.get();
                String status = normalizeStatus(st.getStatus());
                double executed = st.getExecutedQty() == null ? 0.0 : st.getExecutedQty().doubleValue();
                double origQty  = st.getOrigQty() == null ? 0.0 : st.getOrigQty().doubleValue();

                if ("FILLED".equals(status) || (executed >= origQty && origQty > 0.0)) {
                    o.setFilled(true);
                    o.setClosed(true);
                    log.info("refresh: FILLED id={}, executed={}/{}", id, executed, origQty);
                } else if ("CANCELED".equals(status) || "CANCELLED".equals(status)
                           || "EXPIRED".equals(status) || "REJECTED".equals(status)) {
                    o.setCancelled(true);
                    log.info("refresh: {} id={} → помечаем отменённым", status, id);
                } else {
                    // NEW / PENDING_NEW / другие — оставляем как есть
                }
            } catch (Exception e) {
                String msg = String.valueOf(e.getMessage());
                if (msg.contains("order does not exist") || msg.contains("Unknown order")) {
                    o.setCancelled(true);
                    log.info("refresh: 'order does not exist' → помечаем отменённым id={}", id);
                } else {
                    log.debug("Ошибка обновления статуса ордера {}: {}", id, e.getMessage());
                }
            }
        }
    }
}
