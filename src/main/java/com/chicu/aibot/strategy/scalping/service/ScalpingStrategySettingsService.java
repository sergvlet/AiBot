package com.chicu.aibot.strategy.scalping.service;

import com.chicu.aibot.strategy.scalping.model.ScalpingStrategySettings;

public interface ScalpingStrategySettingsService {
    ScalpingStrategySettings getOrCreate(Long chatId);
    void save(ScalpingStrategySettings settings);
}
