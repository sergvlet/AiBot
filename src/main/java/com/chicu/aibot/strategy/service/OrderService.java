package com.chicu.aibot.strategy.service;

import com.chicu.aibot.strategy.model.Order;

import java.util.Collections;
import java.util.List;

/**
 * Сервис для выставления, отмены и закрытия ордеров в стратегиях.
 * Любая торговая стратегия должна использовать этот интерфейс
 * для взаимодействия с биржевым API.
 */
public interface OrderService {

    /**
     * Выставить лимитный ордер.
     *
     * @param chatId   Идентификатор чата/пользователя — используется для выбора API-ключей.
     * @param symbol   Тикер торговой пары, например "BTCUSDT".
     * @param side     Сторона ордера (BUY или SELL).
     * @param price    Цена, по которой выставляется ордер.
     * @param quantity Количество базовой валюты.
     * @return Объект {@link Order} с данными об исполненном ордере.
     */
    Order placeLimit(
        Long chatId,
        String symbol,
        Order.Side side,
        double price,
        double quantity
    );

    /**
     * Выставить рыночный ордер.
     *
     * @param chatId   Идентификатор чата/пользователя — используется для выбора API-ключей.
     * @param symbol   Тикер торговой пары, например "BTCUSDT".
     * @param side     Сторона ордера (BUY или SELL).
     * @param quantity Количество базовой валюты.
     * @return Объект {@link Order} с данными об исполненном ордере.
     */
    Order placeMarket(
        Long chatId,
        String symbol,
        Order.Side side,
        double quantity
    );

    /**
     * Отменить ранее выставленный ордер.
     *
     * @param chatId Идентификатор чата/пользователя.
     * @param order  Объект ордера, который нужно отменить.
     */
    void cancel(Long chatId, Order order);

    /**
     * Закрыть позицию по указанному ордеру.
     * Обычно используется для трейлинг-стопов или ручного закрытия.
     *
     * @param chatId Идентификатор чата/пользователя.
     * @param order  Объект ордера/позиции для закрытия.
     */
    void closePosition(Long chatId, Order order);
    /** Загрузить открытые ордера (из БД/биржи) для первичного заполнения кэша стратегии */
    default List<Order> loadActiveOrders(Long chatId, String symbol) { return Collections.emptyList(); }

    /** Освежить статусы уже известных ордеров (обновить volume/filled/cancelled) */
    default void refreshOrderStatuses(Long chatId, String symbol, List<Order> cache) {}
}
