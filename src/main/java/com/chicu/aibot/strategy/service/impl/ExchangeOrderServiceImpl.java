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

    private String normalizeStatus(String raw) {
        if (raw == null) return "";
        return raw.trim().toUpperCase(Locale.ROOT).replace(" ", "").replace("_", "");
    }

    @Override
    public Order placeLimit(Long chatId, String symbol, Order.Side side, double price, double quantity) {
        return executionService.placeLimit(chatId, symbol, side, price, quantity);
    }

    @Override
    public Order placeMarket(Long chatId, String symbol, Order.Side side, double quantity) {
        return executionService.placeMarket(chatId, symbol, side, quantity);
    }

    @Override
    public void cancel(Long chatId, Order order) {
        log.info("Отмена ордера → id={}, symbol={}", order.getId(), order.getSymbol());

        if (order.getId() == null || order.getId().startsWith("REJECTED-")) {
            order.setCancelled(true);
            log.info("Отмена: локальный REJECTED или пустой id → {}", order.getId());
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
            if (String.valueOf(e.getMessage()).contains("order does not exist")) {
                order.setCancelled(true);
                log.info("Биржа вернула 'order does not exist', помечаем отменённым: {}", order.getId());
            } else {
                log.warn("Ошибка отмены ордера {}: {}", order.getId(), e.getMessage());
            }
        }
    }

    @Override
    public void closePosition(Long chatId, Order order) {
        log.info("Закрыть позицию → {}", order);

        if (order.getVolume() <= 0.0) {
            log.warn("Пропуск закрытия: volume=0");
            return;
        }

        Order.Side opposite = (order.getSide() == Order.Side.BUY) ? Order.Side.SELL : Order.Side.BUY;
        executionService.placeMarket(chatId, order.getSymbol(), opposite, order.getVolume());
        order.setClosed(true);
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

                double price   = oi.getPrice() == null ? 0.0 : oi.getPrice().doubleValue();
                double execQty = oi.getExecutedQty() == null ? 0.0 : oi.getExecutedQty().doubleValue();

                String statusNorm = normalizeStatus(oi.getStatus());
                boolean filled    = "FILLED".equals(statusNorm);

                result.add(new Order(
                        oi.getOrderId(),
                        oi.getSymbol(),
                        side,
                        price,
                        execQty,
                        filled,
                        false,
                        false
                ));
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

            // локальные REJECTED ордера сразу помечаем как отменённые
            if (id.startsWith("REJECTED-")) {
                o.setCancelled(true);
                log.debug("refresh: локальный REJECTED ордер {} помечен отменённым", id);
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

                if (executed > 0.0) {
                    o.setVolume(executed);
                }

                switch (status) {
                    case "FILLED" -> o.setFilled(true);
                    case "PARTIALLY_FILLED" -> {} // оставляем как есть
                    case "CANCELED", "REJECTED", "EXPIRED" -> o.setCancelled(true);
                    default -> {}
                }
            } catch (Exception e) {
                if (String.valueOf(e.getMessage()).contains("order does not exist")) {
                    o.setCancelled(true);
                    log.info("refresh: 'order does not exist' → помечаем отменённым id={}", id);
                } else {
                    log.debug("Ошибка обновления статуса ордера {}: {}", id, e.getMessage());
                }
            }
        }
    }
}
