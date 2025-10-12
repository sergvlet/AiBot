package com.chicu.aibot.strategy.service;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Value @Builder
public class PnLReport {
    BigDecimal realizedPnl;       // суммарный реализованный PnL
    List<Deal> deals;             // последние n сделок (для вывода)

    @Value @Builder
    public static class Deal {
        Instant time;
        String side;              // BUY/SELL
        BigDecimal price;
        BigDecimal qty;
        BigDecimal pnl;           // pnl именно на этой сделке (0 для BUY в FIFO)
        String orderId;
    }
}
