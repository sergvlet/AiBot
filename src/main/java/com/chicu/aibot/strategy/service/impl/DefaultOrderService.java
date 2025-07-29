package com.chicu.aibot.strategy.service.impl;

import com.chicu.aibot.strategy.model.Order;
import com.chicu.aibot.strategy.service.OrderService;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Упрощённая реализация OrderService для тестов.
 * Рыночные ордера считаются сразу исполненными.
 */
@Service
public class DefaultOrderService implements OrderService {

    @Override
    public Order placeLimit(String symbol, Order.Side side, double price, double volume) {
        return new Order(
            UUID.randomUUID().toString(),
            symbol,
            side,
            price,
            volume,
            false, // filled
            false, // cancelled
            false  // closed
        );
    }

    @Override
    public Order placeMarket(String symbol, Order.Side side, double volume) {
        // Рыночный ордер считается сразу исполненным (filled = true)
        return new Order(
            UUID.randomUUID().toString(),
            symbol,
            side,
            0.0,        // Цена неизвестна на момент создания
            volume,
            true,       // filled
            false,      // cancelled
            false       // closed
        );
    }

    @Override
    public void cancel(Order order) {
        order.setCancelled(true);
    }

    @Override
    public void closePosition(Order order) {
        order.setClosed(true);
    }
}
