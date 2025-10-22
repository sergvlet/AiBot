package com.chicu.aibot.strategy.ml_invest.model;

import com.chicu.aibot.strategy.StrategyType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Настройки стратегии Machine Learning Invest
 */
@Entity
@Table(name = "machine_learning_invest_strategy_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MachineLearningInvestStrategySettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long chatId;

    /** Торговая пара (символ), если стратегия работает по одной монете */
    @Builder.Default
    private String symbol = "BTCUSDT";

    /** Путь к модели машинного обучения (.pkl или .joblib) */
    @Builder.Default
    private String modelPath = "python_ml/ml_invest/toy_model/model.pkl";

    /** Размер капитала (квоты) для стратегии */
    @Builder.Default
    private BigDecimal quota = BigDecimal.valueOf(100);

    /** Максимальное количество сделок, открытых одновременно */
    @Builder.Default
    private Integer maxTradesPerQuota = 5;

    /** Размер обучающего окна (в днях) */
    @Builder.Default
    private Integer trainingWindowDays = 30;

    /** Размер универсума (топ-N монет по ликвидности) */
    @Builder.Default
    private Integer universeSize = 20;

    /** Минимальный 24-часовой объём котировки (для фильтрации) */
    @Builder.Default
    private BigDecimal min24hQuoteVolume = BigDecimal.valueOf(1_000_000);

    /** Таймфрейм свечей (например, "1h", "4h") */
    @Builder.Default
    private String timeframe = "1h";

    /** Сколько монет брать для торговли после сортировки по вероятности BUY */
    @Builder.Default
    private Integer tradeTopN = 5;

    /** Использовать ATR-базированный Take Profit / Stop Loss */
    @Builder.Default
    private Boolean useAtrTpSl = false;

    /** Автоматическое переобучение при запуске */
    @Builder.Default
    private Boolean autoRetrainOnStart = true;

    /** Количество кэшируемых свечей (для бэктеста/обучения) */
    @Builder.Default
    private Integer cachedCandlesLimit = 500;

    /** Порог вероятности BUY */
    @Builder.Default
    private BigDecimal buyThreshold = BigDecimal.valueOf(0.55);

    /** Порог вероятности SELL */
    @Builder.Default
    private BigDecimal sellThreshold = BigDecimal.valueOf(0.55);

    /** Take-Profit, % */
    @Builder.Default
    private BigDecimal takeProfitPct = BigDecimal.valueOf(2.0);

    /** Stop-Loss, % */
    @Builder.Default
    private BigDecimal stopLossPct = BigDecimal.valueOf(1.0);

    /** Количество (QTY) для ордера — если используем фиксированный объём */
    @Builder.Default
    private BigDecimal orderQty = BigDecimal.valueOf(0.001);

    /** Сумма (в котировочной валюте), если работаем по quote-amount */
    @Builder.Default
    private BigDecimal orderQuoteAmount = BigDecimal.valueOf(10);

    /** Использовать ли котировочную сумму вместо количества */
    @Builder.Default
    private boolean useQuoteAmount = true;

    /** Активна ли стратегия */
    @Builder.Default
    private boolean active = false;

    /** Тип стратегии — используется в Telegram-боте */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    private StrategyType type = StrategyType.MACHINE_LEARNING_INVEST;
}
