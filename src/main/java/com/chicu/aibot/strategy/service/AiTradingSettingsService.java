package com.chicu.aibot.strategy.service;

import com.chicu.aibot.strategy.StrategyType;
import com.chicu.aibot.strategy.model.AiTradingSettings;

public interface AiTradingSettingsService {
    AiTradingSettings getOrCreate(Long chatId);
    AiTradingSettings get(Long chatId);
    /** Добавляет или убирает стратегию из выбранных */
    void updateSelectedStrategies(Long chatId, StrategyType strategy, boolean selected);
    void save(AiTradingSettings settings);
}
