package com.chicu.aibot.exchange.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SymbolRules {
    /** Минимальное количество (LOT_SIZE.minQty) */
    private BigDecimal minQty;

    /** Шаг количества (LOT_SIZE.stepSize) */
    private BigDecimal stepSize;

    /** Минимальный национал (MIN_NOTIONAL.minNotional) */
    private BigDecimal minNotional;

    /** Минимальная цена (PRICE_FILTER.minPrice) */
    private BigDecimal minPrice;

    /** Шаг цены (PRICE_FILTER.tickSize) */
    private BigDecimal tickSize;

    /** Кол-во знаков для цены/количества (если нужно) */
    private Integer quotePrecision;
    private Integer basePrecision;
}
