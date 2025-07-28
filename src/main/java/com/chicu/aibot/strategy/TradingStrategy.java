package com.chicu.aibot.strategy;


public interface TradingStrategy {
    /** Тип стратегии, который этот бин поддерживает */
    StrategyType getType();

    /** Запустить стратегию для chatId */
    void start(Long chatId);

    /** Остановить стратегию для chatId (отменить все ордера и проч.) */
    void stop(Long chatId);

    /** Вызов при каждом обновлении цены */
    void onPriceUpdate(Long chatId, double price);

    double getCurrentPrice(Long chatId);

}
