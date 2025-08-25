package com.chicu.aibot.strategy.service;

import com.chicu.aibot.strategy.model.Order;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Сервис для выставления, отмены и закрытия ордеров в стратегиях.
 * Любая торговая стратегия должна использовать этот интерфейс
 * для взаимодействия с биржевым API.
 */
public interface OrderService {

    /** Выставить лимитный ордер */
    Order placeLimit(Long chatId,
                     String symbol,
                     Order.Side side,
                     double price,
                     double quantity);

    /** Выставить рыночный ордер */
    Order placeMarket(Long chatId,
                      String symbol,
                      Order.Side side,
                      double quantity);

    /** Отменить ранее выставленный ордер */
    void cancel(Long chatId, Order order);

    /** Закрыть позицию по указанному ордеру */
    void closePosition(Long chatId, Order order);

    /** Загрузить активные (открытые) ордера */
    default List<Order> loadActiveOrders(Long chatId, String symbol) {
        return Collections.emptyList();
    }

    /** Обновить статусы уже известных ордеров */
    default void refreshOrderStatuses(Long chatId, String symbol, List<Order> cache) {}

    /** 📊 Получить всю историю сделок пользователя по символу */
    default List<Order> getTradeHistory(Long chatId, String symbol) {
        return Collections.emptyList();
    }

    /** 📊 Получить историю сделок пользователя по символу за период */
    default List<Order> getTradeHistory(Long chatId, String symbol, Instant from, Instant to) {
        return Collections.emptyList();
    }
}
