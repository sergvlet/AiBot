// src/main/java/com/chicu/aibot/trading/trade/model/TradeLogEntry.java
package com.chicu.aibot.trading.trade.model;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO для отображения сделок в UI.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeLogEntry {

    private Long chatId;
    private String symbol;

    private Instant openTime;
    private Instant closeTime;

    private BigDecimal entryPrice;
    private BigDecimal exitPrice;
    private BigDecimal volume;
    private BigDecimal pnl;
    private BigDecimal pnlPct;

    private String side; // BUY / SELL
}
