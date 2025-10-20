package com.chicu.aibot.strategy.ml_invest.model;

import com.chicu.aibot.strategy.StrategySettings;
import com.chicu.aibot.strategy.StrategyType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "machine_learning_invest_strategy_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
public class MachineLearningInvestStrategySettings extends StrategySettings {

    /** Путь к ML-модели (.joblib/.onnx и т.п.) */
    @Column(name = "model_path")
    private String modelPath;

    /** Таймфрейм (1m/5m/15m/1h/4h/1d) */
    @Column(name = "timeframe")
    private String timeframe;

    /** Кол-во последних свечей для анализа */
    @Column(name = "cached_candles_limit")
    private Integer cachedCandlesLimit;

    /** Порог вероятности для входа в покупку */
    @Column(name = "buy_threshold", precision = 18, scale = 8)
    private BigDecimal buyThreshold;

    /** Порог вероятности для входа в продажу */
    @Column(name = "sell_threshold", precision = 18, scale = 8)
    private BigDecimal sellThreshold;

    /** Take Profit, % */
    @Column(name = "take_profit_pct", precision = 18, scale = 8)
    private BigDecimal takeProfitPct;

    /** Stop Loss, % */
    @Column(name = "stop_loss_pct", precision = 18, scale = 8)
    private BigDecimal stopLossPct;

    /** Торговая пара (например, BTCUSDT) */
    @Column(name = "symbol")
    private String symbol;

    /** Флаг активности стратегии для данного chatId */
    @Builder.Default
    @Column(name = "active", nullable = false)
    private boolean active = false;

    /** ФИКСИРОВАННОЕ кол-во базового актива (исп. если useQuoteAmount = false) */
    @Column(name = "order_qty", precision = 38, scale = 18)
    private BigDecimal orderQty;

    /** ФИКСИРОВАННАЯ СУММА в котируемой валюте (исп. если useQuoteAmount = true) */
    @Column(name = "order_quote_amount", precision = 38, scale = 18)
    private BigDecimal orderQuoteAmount;

    /**
     * Режим расчёта объёма сделки:
     *  true  -> использовать orderQuoteAmount (сумма в котируемой валюте),
     *  false -> использовать orderQty (фикс. количество базового актива).
     */
    @Builder.Default
    @Column(name = "use_quote_amount", nullable = false)
    private boolean useQuoteAmount = false;

    /** Тип стратегии */
    @Override
    public StrategyType getType() {
        return StrategyType.MACHINE_LEARNING_INVEST;
    }
}
