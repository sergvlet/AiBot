package com.chicu.aibot.strategy.bollinger.model;

import com.chicu.aibot.strategy.StrategySettings;
import com.chicu.aibot.strategy.StrategyType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "bollinger_strategy_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class BollingerStrategySettings extends StrategySettings {

    @Column(name = "symbol", nullable = false)
    private String symbol;

    @Column(name = "timeframe", nullable = false)
    private String timeframe;

    @Column(name = "cached_candles_limit", nullable = false)
    private Integer cachedCandlesLimit;

    @Column(name = "order_volume", nullable = false)
    private Double orderVolume;

    @Column(name = "period", nullable = false)
    private Integer period;

    @Column(name = "std_dev_mult", nullable = false)
    private Double stdDevMultiplier;

    @Column(name = "take_profit_pct", nullable = false)
    private Double takeProfitPct;

    @Column(name = "stop_loss_pct", nullable = false)
    private Double stopLossPct;

    @Column(name = "allow_short", nullable = false)
    private Boolean allowShort;

    @Column(name = "allow_long", nullable = false)
    private Boolean allowLong;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Version
    private Long version;

    @Override public StrategyType getType() { return StrategyType.BOLLINGER_BANDS; }
    @Override public String getTimeframe() { return timeframe; }
    @Override public Integer getCachedCandlesLimit() { return cachedCandlesLimit; }
    @Override public boolean isActive() { return active; }
    @Override public void setActive(boolean active) { this.active = active; }
}
