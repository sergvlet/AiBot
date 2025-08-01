package com.chicu.aibot.strategy.service;

import com.chicu.aibot.strategy.model.Order;

/**
 * Любая стратегия через этот интерфейс
 * выставляет / отменяет / закрывает ордера.
 */
public interface OrderService {
    Order placeLimit(
        Long chatId,
        String symbol,
        Order.Side side,
        double price,
        double quantity
    );

    Order placeMarket(
        Long chatId,
        String symbol,
        Order.Side side,
        double quantity
    );

    void cancel(Long chatId, Order order);

    void closePosition(Long chatId, Order order);
}
