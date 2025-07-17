// src/main/java/com/chicu/aibot/bot/strategy/service/impl/DefaultOrderService.java
package com.chicu.aibot.bot.strategy.service.impl;

import com.chicu.aibot.bot.strategy.model.Order;
import com.chicu.aibot.bot.strategy.service.OrderService;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Заглушка: генерирует фиктивные ордера с UUID.
 * Позже замените на вызовы реального API биржи.
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
            false,
            false,
            false
        );
    }

    @Override
    public void cancel(Order o) {
        o.setCancelled(true);
    }

    @Override
    public void closePosition(Order o) {
        o.setClosed(true);
    }
}
