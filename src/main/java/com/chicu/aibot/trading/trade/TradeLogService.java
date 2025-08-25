// src/main/java/com/chicu/aibot/trading/trade/TradeLogService.java
package com.chicu.aibot.trading.trade;

import com.chicu.aibot.trading.trade.model.TradeLogEntry;

import java.util.Optional;

public interface TradeLogService {

    /** Последняя закрытая сделка */
    Optional<TradeLogEntry> getLastTrade(Long chatId, String symbol);

    /** Записать новую сделку */
    void logTrade(TradeLogEntry entry);

    /** Суммарный PnL по символу */
    Optional<Double> getTotalPnl(Long chatId, String symbol);
}
