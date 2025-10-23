package com.chicu.aibot.strategy.ml_invest.model;

import com.chicu.aibot.strategy.StrategyType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Настройки стратегии Machine Learning Invest.
 * Хранит параметры ML-модели, торговые лимиты и выбранные пользователем пары.
 */
@Entity
@Table(name = "machine_learning_invest_strategy_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class MachineLearningInvestStrategySettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Telegram chatId владельца стратегии */
    @Column(nullable = false)
    private Long chatId;

    /** Торговая пара по умолчанию (если стратегия работает с одной монетой) */
    @Builder.Default
    @Column(nullable = false)
    private String symbol = "BTCUSDT";

    /** Путь к ML-модели (.pkl / .joblib) */
    @Builder.Default
    @Column(nullable = false)
    private String modelPath = "python_ml/ml_invest/toy_model/model.pkl";

    /** Квота капитала, выделенная под стратегию */
    @Builder.Default
    @Column(precision = 19, scale = 8)
    private BigDecimal quota = BigDecimal.valueOf(100);

    /** Максимум одновременных сделок */
    @Builder.Default
    private Integer maxTradesPerQuota = 5;

    /** Окно обучения модели (в днях) */
    @Builder.Default
    private Integer trainingWindowDays = 30;

    /** Размер универсума — сколько монет отбирать */
    @Builder.Default
    private Integer universeSize = 20;

    /** Минимальный 24ч объём в котировке (для фильтрации ликвидности) */
    @Builder.Default
    @Column(precision = 19, scale = 8)
    private BigDecimal min24hQuoteVolume = BigDecimal.valueOf(1_000_000);

    /** Таймфрейм, используемый в прогнозах */
    @Builder.Default
    private String timeframe = "1h";

    /** Количество монет, выбранных для торговли по вероятности BUY */
    @Builder.Default
    private Integer tradeTopN = 5;

    /** Использовать ATR-базированный TP/SL */
    @Builder.Default
    private Boolean useAtrTpSl = false;

    /** Переобучать модель при каждом старте */
    @Builder.Default
    private Boolean autoRetrainOnStart = true;

    /** Количество кэшируемых свечей */
    @Builder.Default
    private Integer cachedCandlesLimit = 500;

    /** Порог BUY */
    @Builder.Default
    @Column(precision = 5, scale = 2)
    private BigDecimal buyThreshold = BigDecimal.valueOf(0.55);

    /** Порог SELL */
    @Builder.Default
    @Column(precision = 5, scale = 2)
    private BigDecimal sellThreshold = BigDecimal.valueOf(0.55);

    /** Take-Profit (%) */
    @Builder.Default
    @Column(precision = 5, scale = 2)
    private BigDecimal takeProfitPct = BigDecimal.valueOf(2.0);

    /** Stop-Loss (%) */
    @Builder.Default
    @Column(precision = 5, scale = 2)
    private BigDecimal stopLossPct = BigDecimal.valueOf(1.0);

    /** Количество (QTY) для ордера */
    @Builder.Default
    @Column(precision = 19, scale = 8)
    private BigDecimal orderQty = BigDecimal.valueOf(0.001);

    /** Сумма (в котировочной валюте), если используем quote-amount */
    @Builder.Default
    @Column(precision = 19, scale = 8)
    private BigDecimal orderQuoteAmount = BigDecimal.valueOf(10);

    /** Использовать ли котировочную сумму вместо QTY */
    @Builder.Default
    private boolean useQuoteAmount = true;

    /** Активна ли стратегия */
    @Builder.Default
    private boolean active = false;

    /** Тип стратегии (для унификации в Telegram-боте) */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private StrategyType type = StrategyType.MACHINE_LEARNING_INVEST;

    /** ✅ Список выбранных пользователем торговых пар */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "ml_invest_selected_pairs",
            joinColumns = @JoinColumn(name = "settings_id")
    )
    @Column(name = "symbol", nullable = false, length = 50)
    @Builder.Default
    private List<String> selectedPairs = new ArrayList<>();

    /** Удобный метод добавления пары */
    public void addPair(String symbol) {
        if (selectedPairs == null) {
            selectedPairs = new ArrayList<>();
        }
        if (!selectedPairs.contains(symbol)) {
            selectedPairs.add(symbol);
        }
    }

    /** Удаление пары */
    public void removePair(String symbol) {
        if (selectedPairs != null) {
            selectedPairs.remove(symbol);
        }
    }
}
