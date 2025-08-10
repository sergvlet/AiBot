package com.chicu.aibot.strategy.service.impl;

import com.chicu.aibot.exchange.client.ExchangeClientFactory;
import com.chicu.aibot.exchange.model.ExchangeApiKey;
import com.chicu.aibot.exchange.model.OrderInfo;
import com.chicu.aibot.exchange.model.ExchangeSettings;
import com.chicu.aibot.exchange.service.ExchangeSettingsService;
import com.chicu.aibot.strategy.model.Order;
import com.chicu.aibot.strategy.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeOrderServiceImpl implements OrderService {

    private final ExchangeClientFactory clientFactory;
    private final ExchangeSettingsService settingsService;

    @Override
    public Order placeLimit(Long chatId, String symbol, Order.Side side, double price, double quantity) {
        log.info("Запрос: выставить ЛИМИТ ордер (chatId={}, symbol={}, side={}, price={}, qty={})",
                chatId, symbol, side, price, quantity);

        ExchangeSettings settings = settingsService.getOrCreate(chatId);
        ExchangeApiKey   keys     = settingsService.getApiKey(chatId);
        var client               = clientFactory.getClient(settings.getExchange());

        var req = com.chicu.aibot.exchange.model.OrderRequest.builder()
                .symbol(symbol)
                .side(side == Order.Side.BUY
                        ? com.chicu.aibot.exchange.enums.OrderSide.BUY
                        : com.chicu.aibot.exchange.enums.OrderSide.SELL)
                .type(com.chicu.aibot.exchange.enums.OrderType.LIMIT)
                .quantity(BigDecimal.valueOf(quantity))
                .price(BigDecimal.valueOf(price))
                .build();

        var resp = client.placeOrder(
                keys.getPublicKey(),
                keys.getSecretKey(),
                settings.getNetwork(),
                req
        );

        double executedQty = resp.getExecutedQty().doubleValue();
        String status = resp.getStatus();
        boolean filled = "FILLED".equalsIgnoreCase(status); // корректно считаем filled

        log.info("Лимитный ордер выставлен: id={}, executedQty={}, status={}",
                resp.getOrderId(), executedQty, status);

        return new Order(
                resp.getOrderId(),
                resp.getSymbol(),
                side,
                price,
                executedQty,   // фактически исполненный объём (может быть 0 на NEW)
                filled,        // true только если FILLED
                false,
                false
        );
    }

    @Override
    public Order placeMarket(Long chatId, String symbol, Order.Side side, double quantity) {
        log.info("Запрос: выставить РЫНОЧНЫЙ ордер (chatId={}, symbol={}, side={}, qty={})",
                chatId, symbol, side, quantity);

        ExchangeSettings settings = settingsService.getOrCreate(chatId);
        ExchangeApiKey   keys     = settingsService.getApiKey(chatId);
        var client               = clientFactory.getClient(settings.getExchange());

        var req = com.chicu.aibot.exchange.model.OrderRequest.builder()
                .symbol(symbol)
                .side(side == Order.Side.BUY
                        ? com.chicu.aibot.exchange.enums.OrderSide.BUY
                        : com.chicu.aibot.exchange.enums.OrderSide.SELL)
                .type(com.chicu.aibot.exchange.enums.OrderType.MARKET)
                .quantity(BigDecimal.valueOf(quantity))
                .build();

        var resp = client.placeOrder(
                keys.getPublicKey(),
                keys.getSecretKey(),
                settings.getNetwork(),
                req
        );

        double executedQty = resp.getExecutedQty().doubleValue();
        String status = resp.getStatus();
        boolean filled = "FILLED".equalsIgnoreCase(status);

        log.info("Рыночный ордер исполнен: id={}, executedQty={}, status={}",
                resp.getOrderId(), executedQty, status);

        return new Order(
                resp.getOrderId(),
                resp.getSymbol(),
                side,
                0.0,           // цену можно хранить отдельно, если нужно
                executedQty,
                filled,
                false,
                false
        );
    }

    @Override
    public void cancel(Long chatId, Order order) {
        log.info("Запрос: отмена ордера id={}", order.getId());

        ExchangeSettings settings = settingsService.getOrCreate(chatId);
        ExchangeApiKey   keys     = settingsService.getApiKey(chatId);
        var client               = clientFactory.getClient(settings.getExchange());

        try {
            boolean ok = client.cancelOrder(
                    keys.getPublicKey(),
                    keys.getSecretKey(),
                    settings.getNetwork(),
                    order.getSymbol(),
                    order.getId()
            );
            if (ok) order.setCancelled(true);
            log.info("Отмена ордера id={} {}", order.getId(), ok ? "успех" : "не выполнена");
        } catch (Exception e) {
            log.warn("Ошибка отмены ордера id={}: {}", order.getId(), e.getMessage());
        }
    }

    @Override
    public void closePosition(Long chatId, Order order) {
        log.info("Запрос: закрыть позицию по ордеру id={}", order.getId());

        // не закрываем нулевой объём (лимитка ещё не исполнилась)
        if (order.getVolume() <= 0.0) {
            log.warn("Пропуск закрытия: у ордера id={} нет исполненного объёма (volume={})",
                    order.getId(), order.getVolume());
            return;
        }

        Order.Side opposite = order.getSide() == Order.Side.BUY ? Order.Side.SELL : Order.Side.BUY;
        Order market = placeMarket(chatId, order.getSymbol(), opposite, order.getVolume());

        if (market.getVolume() > 0.0) {
            order.setClosed(true);
            log.info("Позиция закрыта id={} (side={}, qtyClosed={})",
                    order.getId(), opposite, market.getVolume());
        } else {
            log.warn("Не удалось закрыть позицию id={}: market executedQty=0 (filled={})",
                    order.getId(), market.isFilled());
        }
    }

    /* ===== доп. Методы для стратегии ===== */

    @Override
    public List<Order> loadActiveOrders(Long chatId, String symbol) {
        ExchangeSettings settings = settingsService.getOrCreate(chatId);
        ExchangeApiKey   keys     = settingsService.getApiKey(chatId);
        var client               = clientFactory.getClient(settings.getExchange());

        List<Order> result = new ArrayList<>();
        try {
            List<OrderInfo> open = client.fetchOpenOrders(
                    keys.getPublicKey(), keys.getSecretKey(), settings.getNetwork(), symbol);

            for (OrderInfo oi : open) {
                Order.Side side = (oi.getSide() == com.chicu.aibot.exchange.enums.OrderSide.BUY)
                        ? Order.Side.BUY : Order.Side.SELL;

                double price    = oi.getPrice() == null ? 0.0 : oi.getPrice().doubleValue();
                double execQty  = oi.getExecutedQty() == null ? 0.0 : oi.getExecutedQty().doubleValue();
                boolean filled  = "FILLED".equalsIgnoreCase(oi.getStatus()); // обычно открытые не FILLED

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
            log.info("Загружено {} открытых ордеров по {} (chatId={})", result.size(), symbol, chatId);
        } catch (Exception e) {
            log.warn("loadActiveOrders({}, {}) ошибка: {}", chatId, symbol, e.getMessage());
        }
        return result;
    }

    @Override
    public void refreshOrderStatuses(Long chatId, String symbol, List<Order> localOrders) {
        if (localOrders == null || localOrders.isEmpty()) return;

        ExchangeSettings settings = settingsService.getOrCreate(chatId);
        ExchangeApiKey   keys     = settingsService.getApiKey(chatId);
        var client               = clientFactory.getClient(settings.getExchange());

        for (Order o : localOrders) {
            if (o.isCancelled() || o.isClosed()) continue;
            String id = o.getId();
            if (id == null) continue;

            try {
                var opt = client.fetchOrder(
                        keys.getPublicKey(), keys.getSecretKey(), settings.getNetwork(), symbol, id);
                if (opt.isEmpty()) continue;

                OrderInfo st = opt.get();
                String status = st.getStatus() == null ? "" : st.getStatus();

                double executed = st.getExecutedQty() == null ? 0.0 : st.getExecutedQty().doubleValue();
                if (executed > 0) {
                    o.setVolume(executed); // отражаем частичное/полное исполнение
                }

                switch (status) {
                    case "FILLED" -> o.setFilled(true);
                    case "PARTIALLY_FILLED" -> { /* остаётся filled=false, но объём уже обновили */ }
                    case "CANCELED", "REJECTED", "EXPIRED" -> o.setCancelled(true);
                    default -> { /* NEW / др. */ }
                }
            } catch (Exception e) {
                log.debug("refresh status failed for id={}: {}", id, e.getMessage());
            }
        }
    }
}
