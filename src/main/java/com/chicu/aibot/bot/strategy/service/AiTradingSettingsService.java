// src/main/java/com/chicu/aibot/bot/strategy/service/AiTradingSettingsService.java
package com.chicu.aibot.bot.strategy.service;

import com.chicu.aibot.bot.strategy.StrategyType;
import com.chicu.aibot.bot.strategy.model.AiTradingSettings;

public interface AiTradingSettingsService {
    AiTradingSettings getOrCreate(Long chatId);
    AiTradingSettings get(Long chatId);
    /** Добавляет или убирает стратегию из выбранных */
    void updateSelectedStrategies(Long chatId, StrategyType strategy, boolean selected);
    void save(AiTradingSettings settings);
}
