// src/main/java/com/chicu/aibot/bot/strategy/service/impl/AiTradingSettingsServiceImpl.java
package com.chicu.aibot.bot.strategy.service.impl;

import com.chicu.aibot.bot.strategy.StrategyType;
import com.chicu.aibot.bot.strategy.model.AiTradingSettings;
import com.chicu.aibot.bot.strategy.repository.AiTradingSettingsRepository;
import com.chicu.aibot.bot.strategy.service.AiTradingSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;

@Service
@RequiredArgsConstructor
public class AiTradingSettingsServiceImpl implements AiTradingSettingsService {

    private final AiTradingSettingsRepository repo;

    @Override
    @Transactional(readOnly = true)
    public AiTradingSettings getOrCreate(Long chatId) {
        return repo.findById(chatId).orElseGet(() -> {
            AiTradingSettings s = AiTradingSettings.builder()
                    .chatId(chatId)
                    .selectedStrategies(new HashSet<>())
                    .build();
            return repo.save(s);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public AiTradingSettings get(Long chatId) {
        return repo.findById(chatId)
                   .orElseThrow(() -> new IllegalStateException("AiTradingSettings not found for chatId=" + chatId));
    }

    @Override
    @Transactional
    public void updateSelectedStrategies(Long chatId, StrategyType strategy, boolean selected) {
        AiTradingSettings s = getOrCreate(chatId);
        if (selected) {
            s.getSelectedStrategies().add(strategy);
        } else {
            s.getSelectedStrategies().remove(strategy);
        }
        repo.save(s);
    }

    @Override
    @Transactional
    public void save(AiTradingSettings settings) {
        repo.save(settings);
    }
}
