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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeOrderServiceImpl implements OrderService {

    private final ExchangeClientFactory clientFactory;
    private final ExchangeSettingsService settingsService;
    private final OrderExecutionService executionService; // вынесена логика placeLimit/placeMarket

    private String normalizeStatus(String raw) {
        if (raw == null) return "";
        return raw.trim().toUpperCase(Locale.ROOT).replace(" ", "").replace("_", "");
    }

    private int priceCompare(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        return a.stripTrailingZeros().compareTo(b.stripTrailingZeros());
    }

    /**
     * Проверка: есть ли уже открытая лимитка на том же уровне и той же стороне.
     * Нужны ключи, т.к. берём открытые ордера с биржи.
     */
    private boolean existsOpenLimit(Long chatId, String symbol, Order.Side side, BigDecimal price) {
        try {
            var settings = settingsService.getOrCreate(chatId);
            var keys     = settingsService.getApiKey(chatId);
            ExchangeClient client = clientFactory.getClient(settings.getExchange());

            List<OrderInfo> openOrders = client.fetchOpenOrders(
                    keys.getPublicKey(),
                    keys.getSecretKey(),
                    settings.getNetwork(),
                    symbol
            );

            OrderSide exSide = (side == Order.Side.BUY) ? OrderSide.BUY : OrderSide.SELL;

            for (OrderInfo oi : openOrders) {
                if (!Objects.equals(symbol, oi.getSymbol())) continue;
                if (oi.getSide() != exSide) continue;

                // Цена может быть null для MARKET, но у нас лимитки — сравниваем аккуратно
                BigDecimal p = oi.getPrice();
                if (priceCompare(p, price) == 0) {
                    log.debug("Найдена существующая лимитка {} {} @{} (id={}) — дубликат",
                            symbol, side, p, oi.getOrderId());
                    return true;
                }
            }
        } catch (Exception e) {
            // Не валим процесс из-за сетевой/биржевой ошибки — просто позволим поставить ордер
            log.warn("existsOpenLimit: не удалось проверить дубликаты: {}", e.getMessage());
        }
        return false;
    }

    @Override
    public Order placeLimit(Long chatId, String symbol, Order.Side side, double price, double quantity) {
        // Анти-дубликаты: не ставим такую же лимитку на том же уровне
        if (existsOpenLimit(chatId, symbol, side, BigDecimal.valueOf(price))) {
            log.info("Пропуск постановки дубликата лимитки: {} {} @{} qty={}", symbol, side, price, quantity);
            // Возвращаем "псевдо-ордер" без id, чтобы верхний слой не считал это ошибкой
            return new Order(
                    "SKIPPED-DUPLICATE",
                    symbol,
                    side,
                    price,
                    quantity,
                    false,
                    false,
                    false
            );
        }
        // Поручаем реальную постановку общему исполнителю (там нормализация, GTC и т. п.)
        return executionService.placeLimit(chatId, symbol, side, price, quantity);
    }

    @Override
    public Order placeMarket(Long chatId, String symbol, Order.Side side, double quantity) {
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

        var settings = settingsService.getOrCreate(chatId);
        var keys     = settingsService.getApiKey(chatId);
        ExchangeClient client = clientFactory.getClient(settings.getExchange());

        try {
            boolean ok = client.cancelOrder(
                    keys.getPublicKey(),
                    keys.getSecretKey(),
                    settings.getNetwork(),
                    order.getSymbol(),
                    order.getId()
            );
            order.setCancelled(ok);
            log.info("Отмена ордера {}: {}", order.getId(), ok ? "успешно" : "не выполнена");
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

        // Закрываем MARKET — без IOC/FOK лимиток, которые истекают
        try {
            executionService.placeMarket(chatId, order.getSymbol(), opposite, order.getVolume());
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
                boolean canceled  = "CANCELED".equals(statusNorm) || "EXPIRED".equals(statusNorm) || "REJECTED".equals(statusNorm);

                if (!canceled) {
                    result.add(new Order(
                            oi.getOrderId(),
                            oi.getSymbol(),
                            side,
                            price,
                            origQty,   // ВАЖНО: объём ордера — это исходный объём, а не executed
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
        if (cache == null || cache.isEmpty()) return;

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

                // НЕ перезаписываем объём ордера executed-количеством!
                // if (executed > 0.0) o.setVolume(executed);  ← это было источником qty=0E-8 в логах

                switch (status) {
                    case "FILLED" -> o.setFilled(true);
                    case "PARTIALLYFILLED", "PARTIALLY_FILLED" -> {
                        // оставляем открытым; при необходимости можно логировать прогресс
                        if (origQty > 0 && executed >= origQty) o.setFilled(true);
                    }
                    case "CANCELED", "REJECTED", "EXPIRED" -> o.setCancelled(true);
                    default -> {
                        // NEW / PENDING_NEW / другие — оставляем как есть
                    }
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
