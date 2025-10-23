package com.chicu.aibot.strategy.ml_invest.service;

import com.chicu.aibot.strategy.ml_invest.model.MachineLearningInvestStrategySettings;

import java.util.Optional;

public interface MachineLearningInvestStrategySettingsService {

    MachineLearningInvestStrategySettings get(Long chatId);

    MachineLearningInvestStrategySettings getOrCreate(Long chatId);

    MachineLearningInvestStrategySettings save(MachineLearningInvestStrategySettings settings);

    MachineLearningInvestStrategySettings updateMaxTrades(Long chatId, Integer maxTrades);

    /** Найти настройки по chatId (Optional). */
    Optional<MachineLearningInvestStrategySettings> findByChatId(Long chatId);
}
