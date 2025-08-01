package com.chicu.aibot.strategy.service.impl;

import com.chicu.aibot.exchange.client.ExchangeClient;
import com.chicu.aibot.exchange.client.ExchangeClientFactory;
import com.chicu.aibot.exchange.enums.OrderSide;
import com.chicu.aibot.exchange.enums.OrderType;
import com.chicu.aibot.exchange.model.ExchangeApiKey;
import com.chicu.aibot.exchange.model.OrderRequest;
import com.chicu.aibot.exchange.model.OrderResponse;
import com.chicu.aibot.exchange.model.ExchangeSettings;
import com.chicu.aibot.exchange.service.ExchangeSettingsService;
import com.chicu.aibot.strategy.model.Order;
import com.chicu.aibot.strategy.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ExchangeOrderServiceImpl implements OrderService {

    private final ExchangeClientFactory clientFactory;
    private final ExchangeSettingsService settingsService;

    @Override
    public Order placeLimit(Long chatId, String symbol, Order.Side side, double price, double quantity) {
        ExchangeSettings s = settingsService.getOrCreate(chatId);
        ExchangeApiKey keys = settingsService.getApiKey(chatId);

        ExchangeClient client = clientFactory.getClient(s.getExchange());  // принимает Enum :contentReference[oaicite:11]{index=11}

        OrderRequest req = OrderRequest.builder()
            .symbol(symbol)
            .side(side == Order.Side.BUY ? OrderSide.BUY : OrderSide.SELL)
            .type(OrderType.LIMIT)
            .quantity(BigDecimal.valueOf(quantity))
            .price(BigDecimal.valueOf(price))
            .build();

        OrderResponse resp = client.placeOrder(
            keys.getPublicKey(),
            keys.getSecretKey(),
            s.getNetwork(),
            req
        );

        return new Order(
            resp.getOrderId(),
            resp.getSymbol(),
            side,
            resp.getExecutedQty().doubleValue(),
            resp.getExecutedQty().doubleValue(),
            true,
            false,
            false
        );
    }

    @Override
    public Order placeMarket(Long chatId, String symbol, Order.Side side, double quantity) {
        ExchangeSettings s = settingsService.getOrCreate(chatId);
        ExchangeApiKey keys = settingsService.getApiKey(chatId);

        ExchangeClient client = clientFactory.getClient(s.getExchange());

        OrderRequest req = OrderRequest.builder()
            .symbol(symbol)
            .side(side == Order.Side.BUY ? OrderSide.BUY : OrderSide.SELL)
            .type(OrderType.MARKET)
            .quantity(BigDecimal.valueOf(quantity))
            .build();

        OrderResponse resp = client.placeOrder(
            keys.getPublicKey(),
            keys.getSecretKey(),
            s.getNetwork(),
            req
        );

        return new Order(
            resp.getOrderId(),
            resp.getSymbol(),
            side,
            resp.getExecutedQty().doubleValue(),
            resp.getExecutedQty().doubleValue(),
            true,
            false,
            false
        );
    }

    @Override
    public void cancel(Long chatId, Order order) {
        // можно расширить: client.cancelOrder(...)
        order.setCancelled(true);
    }

    @Override
    public void closePosition(Long chatId, Order order) {
        // можно расширить: market-ордер противоположного side
        order.setClosed(true);
    }
}
