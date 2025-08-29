package com.chicu.aibot.exchange.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Унифицированная модель баланса для всех бирж.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceInfo {

    private String asset;        // тикер (например: BTC, USDT)
    private BigDecimal free;     // доступный баланс
    private BigDecimal locked;   // замороженный баланс (в ордерах)

    /**
     * @return общий баланс (free + locked)
     */
    public BigDecimal getTotal() {
        return free.add(locked);
    }
}
