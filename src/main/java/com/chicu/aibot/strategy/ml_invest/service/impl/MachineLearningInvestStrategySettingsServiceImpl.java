package com.chicu.aibot.strategy.ml_invest.service.impl;

import com.chicu.aibot.strategy.ml_invest.model.MachineLearningInvestStrategySettings;
import com.chicu.aibot.strategy.ml_invest.repository.MachineLearningInvestStrategySettingsRepository;
import com.chicu.aibot.strategy.ml_invest.service.MachineLearningInvestStrategySettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MachineLearningInvestStrategySettingsServiceImpl implements MachineLearningInvestStrategySettingsService {

    private final MachineLearningInvestStrategySettingsRepository repository;

    @Override
    @Transactional(readOnly = true)
    public MachineLearningInvestStrategySettings get(Long chatId) {
        return repository.findByChatId(chatId).orElse(null);
    }

    @Override
    @Transactional
    public MachineLearningInvestStrategySettings getOrCreate(Long chatId) {
        return repository.findByChatId(chatId)
                .orElseGet(() -> {
                    var s = MachineLearningInvestStrategySettings.builder()
                            .chatId(chatId)
                            .symbol("BTCUSDT")
                            .maxTradesPerQuota(5)
                            .trainingWindowDays(30)
                            .universeSize(20)
                            .timeframe("1h")
                            .build();
                    var saved = repository.save(s);
                    log.info("[ML-Invest] Созданы новые настройки по chatId={}", chatId);
                    return saved;
                });
    }

    @Override
    @Transactional
    public MachineLearningInvestStrategySettings save(MachineLearningInvestStrategySettings settings) {
        return repository.save(settings);
    }

    /** ✅ Возвращаем обновлённый объект, как требует интерфейс */
    @Override
    @Transactional
    public MachineLearningInvestStrategySettings updateMaxTrades(Long chatId, Integer maxTrades) {
        var settings = repository.findByChatId(chatId)
                .orElseThrow(() -> new IllegalStateException("Настройки ML-Invest не найдены для chatId=" + chatId));
        settings.setMaxTradesPerQuota(maxTrades);
        var updated = repository.save(settings);
        log.info("[ML-Invest] Обновлено maxTradesPerQuota={} для chatId={}", maxTrades, chatId);
        return updated;
    }
}
