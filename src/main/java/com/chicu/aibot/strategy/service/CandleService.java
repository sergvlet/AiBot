package com.chicu.aibot.strategy.service;

import com.chicu.aibot.strategy.model.Candle;

import java.util.List;

/**
 * Любая стратегия может через этот интерфейс
 * запросить историю свечей.
 */
public interface CandleService {
    /**
     * @param symbol    торговая пара, напр. "BTCUSDT"
     * @param timeframe таймфрейм, напр. "1h"
     * @param limit     сколько последних свечей вернуть
     */
    List<Candle> getLastCandles(String symbol, String timeframe, int limit);
}
