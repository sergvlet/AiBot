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

    /** Торговый символ, например "BTCUSDT" */
    @Column(name = "symbol", nullable = false)
    private String symbol;

    /**
     * Уровни Фибоначчи (в виде десятичных долей, например 0.382, 0.618 и т.д.)
     * Загружаем EAGER, чтобы избежать LazyInitializationException при рендер меню.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "fibonacci_grid_levels",
            joinColumns = @JoinColumn(name = "chat_id")
    )
    @Column(name = "level", nullable = false)
    private List<Double> levels;

    /** Шаг сетки в процентах (расстояние между соседними ордерами) */
    @Column(name = "grid_size_pct", nullable = false)
    private Double gridSizePct;

    /** Объявленный объём (кол-во базовой валюты) для каждого порядка */
    @Column(name = "order_volume", nullable = false)
    private Double orderVolume;

    /** Максимальное число одновременно открытых ордеров */
    @Column(name = "max_active_orders", nullable = false)
    private Integer maxActiveOrders;

    /** Take-Profit для всей сетки в процентах от средней цены всех ордеров */
    @Column(name = "take_profit_pct", nullable = false)
    private Double takeProfitPct;

    /** Stop-Loss в процентах от входа (выключается при проскальзывании ниже минимального уровня) */
    @Column(name = "stop_loss_pct", nullable = false)
    private Double stopLossPct;

    /** Разрешено ли открытие коротких позиций (short) */
    @Column(name = "allow_short", nullable = false)
    private Boolean allowShort;

    /** Разрешено ли открытие длинных позиций (long) */
    @Column(name = "allow_long", nullable = false)
    private Boolean allowLong;

    /** Таймфрейм, напр. "1h" */
    @Column(name = "timeframe", nullable = false)
    private String timeframe;

    /** Сколько свечей брать из API для расчётов */
    @Column(name = "cached_candles_limit", nullable = false)
    private Integer cachedCandlesLimit;

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
}
