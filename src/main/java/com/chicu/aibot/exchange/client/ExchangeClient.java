package com.chicu.aibot.exchange.client;

import com.chicu.aibot.exchange.enums.NetworkType;
import com.chicu.aibot.exchange.model.*;
import com.chicu.aibot.strategy.model.Candle;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public interface ExchangeClient {

    /**
     * Проверка валидности ключей и доступности API.
     */
    boolean testConnection(String apiKey, String secretKey, NetworkType networkType);

    /**
     * Получение информации по аккаунту: балансы, активы.
     */
    AccountInfo fetchAccountInfo(String apiKey, String secretKey, NetworkType networkType);

    /**
     * Размещение ордера (market/limit и т. д.).
     */
    OrderResponse placeOrder(
            String apiKey,
            String secretKey,
            NetworkType networkType,
            OrderRequest orderRequest
    );

    /**
     * Вернуть список самых популярных торговых пар.
     */
    List<String> fetchPopularSymbols();

    /**
     * Лидеры роста за 24 часа.
     */
    List<String> fetchGainers();

    /**
     * Лидеры падения за 24 часа.
     */
    List<String> fetchLosers();

    /**
     * По объёму торгов за 24 часа.
     */
    List<String> fetchByVolume();

    /**
     * Возвращает текущую цену и процентное изменение за 24ч.
     */
    TickerInfo getTicker(String symbol, NetworkType networkType);

    /**
     * Получение исторических свечей (OHLCV).
     *
     * @param apiKey      публичный ключ API
     * @param secretKey   секретный ключ API
     * @param networkType сеть (SPOT, FUTURES и т.п.)
     * @param symbol      тикер, например "BTCUSDT"
     * @param timeframe   интервал свечи, например "1m", "5m", "1h"
     * @param limit       количество свечей
     * @return список свечей в виде модели {@link Candle}
     */
    List<Candle> fetchCandles(
            String apiKey,
            String secretKey,
            NetworkType networkType,
            String symbol,
            String timeframe,
            int limit
    );

    List<OrderInfo> getOpenOrders(String apiKey, String secretKey, NetworkType networkType, String symbol);

    OrderInfo getOrder(String apiKey, String secretKey, NetworkType networkType, String symbol, String orderId);

    /**
     * Открытые ордера по символу
     */
    default List<OrderInfo> fetchOpenOrders(String apiKey, String secretKey, NetworkType network, String symbol) {
        return Collections.emptyList();
    }

    /**
     * Получить информацию по конкретному ордеру
     */
    default Optional<OrderInfo> fetchOrder(String apiKey, String secretKey, NetworkType network, String symbol, String orderId) {
        return Optional.empty();
    }

    /**
     * Отменить ордер
     */
    default boolean cancelOrder(String apiKey, String secretKey, NetworkType network, String symbol, String orderId) {
        return false;
    }
}
