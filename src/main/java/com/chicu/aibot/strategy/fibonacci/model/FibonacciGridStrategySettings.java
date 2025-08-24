package com.chicu.aibot.strategy.fibonacci.model;

import com.chicu.aibot.strategy.StrategySettings;
import com.chicu.aibot.strategy.StrategyType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Entity
@Table(name = "fibonacci_grid_strategy_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class FibonacciGridStrategySettings extends StrategySettings {

    @Column(name = "symbol", nullable = false)
    private String symbol;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "fibonacci_grid_levels", joinColumns = @JoinColumn(name = "chat_id"))
    @Column(name = "level", nullable = false)
    private List<Double> levels;

    @Column(name = "grid_size_pct", nullable = false)
    private Double gridSizePct;

    @Column(name = "order_volume", nullable = false)
    private Double orderVolume;

    @Column(name = "max_active_orders", nullable = false)
    private Integer maxActiveOrders;

    @Column(name = "take_profit_pct", nullable = false)
    private Double takeProfitPct;

    @Column(name = "stop_loss_pct", nullable = false)
    private Double stopLossPct;

    @Column(name = "allow_short", nullable = false)
    private Boolean allowShort;

    @Column(name = "allow_long", nullable = false)
    private Boolean allowLong;

    @Column(name = "timeframe", nullable = false)
    private String timeframe;

    @Column(name = "cached_candles_limit", nullable = false)
    private Integer cachedCandlesLimit;

    @Column(name = "active", nullable = false)
    private boolean active; // поддержка запуска/остановки

    @Version
    private Long version;

    @Override
    public StrategyType getType() {
        return StrategyType.FIBONACCI_GRID;
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

    /* ---------- ДОБАВЛЕНО: boolean-геттеры под вызовы isAllowLong()/isAllowShort() ---------- */

    /** Null-safe: трактуем null как false. */
    public boolean isAllowLong() {
        return Boolean.TRUE.equals(allowLong);
    }

    /** Null-safe: трактуем null как false. */
    public boolean isAllowShort() {
        return Boolean.TRUE.equals(allowShort);
    }
}
