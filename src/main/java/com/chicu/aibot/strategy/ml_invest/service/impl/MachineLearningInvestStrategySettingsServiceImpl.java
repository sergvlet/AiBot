package com.chicu.aibot.strategy.ml_invest.service.impl;

import com.chicu.aibot.strategy.ml_invest.model.MachineLearningInvestStrategySettings;
import com.chicu.aibot.strategy.ml_invest.repository.MachineLearningInvestStrategySettingsRepository;
import com.chicu.aibot.strategy.ml_invest.service.MachineLearningInvestStrategySettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Optional;

/**
 * Реализация сервиса управления настройками стратегии Machine Learning Invest.
 * Обеспечивает создание, сохранение и актуализацию параметров (включая выбранные пары).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MachineLearningInvestStrategySettingsServiceImpl implements MachineLearningInvestStrategySettingsService {

    private final MachineLearningInvestStrategySettingsRepository repository;

    /**
     * Получить настройки без создания, если не найдены — вернуть null.
     */
    @Override
    @Transactional(readOnly = true)
    public MachineLearningInvestStrategySettings get(Long chatId) {
        return repository.findByChatId(chatId).orElse(null);
    }

    /**
     * Получить или создать настройки по chatId с дефолтными значениями.
     */
    @Override
    @Transactional
    public MachineLearningInvestStrategySettings getOrCreate(Long chatId) {
        return repository.findByChatId(chatId)
                .orElseGet(() -> {
                    MachineLearningInvestStrategySettings s = MachineLearningInvestStrategySettings.builder()
                            .chatId(chatId)
                            .symbol("BTCUSDT")
                            .modelPath("python_ml/ml_invest/toy_model/model.pkl")
                            .quota(java.math.BigDecimal.valueOf(100))
                            .maxTradesPerQuota(5)
                            .trainingWindowDays(30)
                            .universeSize(20)
                            .min24hQuoteVolume(java.math.BigDecimal.valueOf(1_000_000))
                            .timeframe("1h")
                            .tradeTopN(5)
                            .useAtrTpSl(false)
                            .autoRetrainOnStart(true)
                            .cachedCandlesLimit(500)
                            .buyThreshold(java.math.BigDecimal.valueOf(0.55))
                            .sellThreshold(java.math.BigDecimal.valueOf(0.55))
                            .takeProfitPct(java.math.BigDecimal.valueOf(2.0))
                            .stopLossPct(java.math.BigDecimal.valueOf(1.0))
                            .orderQty(java.math.BigDecimal.valueOf(0.001))
                            .orderQuoteAmount(java.math.BigDecimal.valueOf(10))
                            .useQuoteAmount(true)
                            .active(false)
                            // ✅ пустой список по умолчанию
                            .selectedPairs(new ArrayList<>())
                            .build();

                    // ✅ добавляем символ по умолчанию в selectedPairs
                    s.getSelectedPairs().add(s.getSymbol());

                    MachineLearningInvestStrategySettings saved = repository.save(s);
                    log.info("[ML-Invest] ✅ Созданы новые настройки для chatId={}, symbol={}", chatId, s.getSymbol());
                    return saved;
                });
    }

    /**
     * Сохранить или обновить существующие настройки.
     */
    @Override
    @Transactional
    public MachineLearningInvestStrategySettings save(MachineLearningInvestStrategySettings settings) {
        // гарантируем, что selectedPairs не null
        if (settings.getSelectedPairs() == null) {
            settings.setSelectedPairs(new ArrayList<>());
        }

        // ✅ если есть выбранный symbol — добавляем его в selectedPairs
        if (settings.getSymbol() != null &&
            !settings.getSymbol().isBlank() &&
            !settings.getSelectedPairs().contains(settings.getSymbol())) {
            settings.getSelectedPairs().add(settings.getSymbol());
            log.info("[ML-Invest] 🔗 Добавлен символ '{}' в список выбранных пар chatId={}",
                    settings.getSymbol(), settings.getChatId());
        }

        MachineLearningInvestStrategySettings saved = repository.save(settings);
        log.debug("[ML-Invest] 💾 Сохранены настройки chatId={} symbol={} pairs={}",
                settings.getChatId(),
                settings.getSymbol(),
                settings.getSelectedPairs().size());
        return saved;
    }

    /**
     * Обновить параметр maxTradesPerQuota.
     */
    @Override
    @Transactional
    public MachineLearningInvestStrategySettings updateMaxTrades(Long chatId, Integer maxTrades) {
        MachineLearningInvestStrategySettings settings = repository.findByChatId(chatId)
                .orElseThrow(() -> new IllegalStateException(
                        "Настройки ML-Invest не найдены для chatId=" + chatId));

        settings.setMaxTradesPerQuota(maxTrades);
        MachineLearningInvestStrategySettings updated = repository.save(settings);

        log.info("[ML-Invest] 🔁 Обновлено maxTradesPerQuota={} для chatId={}", maxTrades, chatId);
        return updated;
    }

    /**
     * Найти настройки по chatId (Optional).
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<MachineLearningInvestStrategySettings> findByChatId(Long chatId) {
        return repository.findByChatId(chatId);
    }
}
