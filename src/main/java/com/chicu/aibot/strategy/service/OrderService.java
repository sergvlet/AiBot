package com.chicu.aibot.strategy.service;

import com.chicu.aibot.strategy.model.Order;

/**
 * Любая стратегия через этот интерфейс
 * выставляет / отменяет / закрывает ордера.
 */
public interface OrderService {
    /**
     * Выставить лимитный ордер.
     * @return новый объект Order (с id, ценой, объёмом и т.д.)
     */
    Order placeLimit(String symbol, Order.Side side, double price, double quantity);

    /** Отменить незакрытый ордер */
    void cancel(Order order);

    /** Закрыть всё по исполненному ордеру (market или противоположным лимитом) */
    void closePosition(Order order);

    Order placeMarket(String symbol, Order.Side side, double quantity);}
