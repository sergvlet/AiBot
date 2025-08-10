// File: src/main/java/com/chicu/aibot/strategy/service/MarketDataClient.java
package com.chicu.aibot.strategy.service;

/**
 * Интерфейс для получения текущей цены с учётом настроек API-ключей.
 */
public interface MarketDataClient {
    /**
     * @param chatId  ID чата/пользователя — для выбора API-ключей и сети.
     * @param symbol  Тикер, например "BTCUSDT".
     * @return Текущая цена.
     */
    double getLastPrice(Long chatId, String symbol);
}
