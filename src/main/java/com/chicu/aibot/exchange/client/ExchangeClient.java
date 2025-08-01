package com.chicu.aibot.exchange.client;

import com.chicu.aibot.exchange.enums.NetworkType;
import com.chicu.aibot.exchange.model.AccountInfo;
import com.chicu.aibot.exchange.model.OrderRequest;
import com.chicu.aibot.exchange.model.OrderResponse;
import com.chicu.aibot.exchange.model.TickerInfo;

import java.util.List;

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

    /** Лидеры роста за 24 часа. */
    List<String> fetchGainers();

    /** Лидеры падения за 24 часа. */
    List<String> fetchLosers();

    /** По объёму торгов за 24 часа. */
    List<String> fetchByVolume();

    /**
     * Возвращает текущую цену и процентное изменение за 24ч.
     */
    TickerInfo getTicker(String symbol, NetworkType networkType);


}
