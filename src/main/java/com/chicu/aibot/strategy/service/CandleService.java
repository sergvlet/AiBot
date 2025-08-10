package com.chicu.aibot.strategy.service;

import com.chicu.aibot.strategy.model.Candle;
import java.util.List;

public interface CandleService {
    /**
     * Вернуть последние исторические свечи по символу для данного чата.
     *
     * @param chatId    идентификатор чата (токены, сеть и пр. для этого чата)
     * @param symbol    тикер, например "BTCUSDT"
     * @param timeframe "1m", "5m", "1h" и т.д.
     * @param limit     сколько свечей вернуть
     */
    List<Candle> getCandles(Long chatId, String symbol, String timeframe, int limit);
}
