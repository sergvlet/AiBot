package com.chicu.aibot.strategy.ml_invest.service;


import com.chicu.aibot.strategy.ml_invest.model.MachineLearningInvestStrategySettings;

public interface MachineLearningInvestStrategySettingsService {
    MachineLearningInvestStrategySettings getOrCreate(Long chatId);
    MachineLearningInvestStrategySettings save(MachineLearningInvestStrategySettings settings);
}
