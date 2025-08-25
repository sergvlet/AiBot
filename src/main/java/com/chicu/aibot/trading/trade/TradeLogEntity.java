// src/main/java/com/chicu/aibot/trading/trade/TradeLogEntity.java
package com.chicu.aibot.trading.trade;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "trade_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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
