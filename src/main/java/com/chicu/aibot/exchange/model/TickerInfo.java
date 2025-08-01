// src/main/java/com/chicu/aibot/exchange/model/TickerInfo.java
package com.chicu.aibot.exchange.model;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

/**
 * Информация о цене и изменении за 24ч для одного символа.
 */
@Data
@Builder
public class TickerInfo {
    /** Текущая цена */
    private BigDecimal price;
    /** Процент изменения за 24 часа, например +5.23 или -1.45 */
    private BigDecimal changePct;
}
