package com.chicu.aibot.strategy.scalping.model;

import com.chicu.aibot.strategy.StrategySettings;
import com.chicu.aibot.strategy.StrategyType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "scalping_strategy_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class ScalpingStrategySettings extends StrategySettings {

    @Column(name = "symbol", nullable = false)
    private String symbol;

    @Column(name = "window_size", nullable = false)
    private Integer windowSize;

    @Column(name = "price_change_threshold", nullable = false)
    private Double priceChangeThreshold;

    @Column(name = "order_volume", nullable = false)
    private Double orderVolume;

    @Column(name = "spread_threshold", nullable = false)
    private Double spreadThreshold;

    @Column(name = "take_profit_pct", nullable = false)
    private Double takeProfitPct;

    @Column(name = "stop_loss_pct", nullable = false)
    private Double stopLossPct;

    @Column(name = "timeframe", nullable = false)
    private String timeframe;

    @Column(name = "cached_candles_limit", nullable = false)
    private Integer cachedCandlesLimit;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Override
    public StrategyType getType() {
        return StrategyType.SCALPING;
    }

    @Override
    public String getTimeframe() {
        return timeframe;
    }

    @Override
    public Integer getCachedCandlesLimit() {
        return cachedCandlesLimit;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void setActive(boolean active) {
        this.active = active;
    }
}
