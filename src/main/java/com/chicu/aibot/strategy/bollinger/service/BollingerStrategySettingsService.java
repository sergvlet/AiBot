package com.chicu.aibot.strategy.bollinger.service;

import com.chicu.aibot.strategy.bollinger.model.BollingerStrategySettings;


public interface BollingerStrategySettingsService {
    BollingerStrategySettings getOrCreate(Long chatId);
    BollingerStrategySettings save(BollingerStrategySettings s);


}
