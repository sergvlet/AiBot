// src/main/java/com/chicu/aibot/exchange/model/SymbolFilters.java
package com.chicu.aibot.exchange.model;

import lombok.*;
import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SymbolFilters {
    /** LOT_SIZE.stepSize */
    private BigDecimal stepSize;
    /** LOT_SIZE.minQty */
    private BigDecimal minQty;
    /** MIN_NOTIONAL.minNotional */
    private BigDecimal minNotional;
}
