package com.chicu.aibot.strategy.model;

import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Модель свечи из API биржи.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Candle {
    private String symbol;
    private Instant openTime;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private BigDecimal volume;
}
