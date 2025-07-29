package com.chicu.aibot.strategy.scalping.service.impl;

import com.chicu.aibot.strategy.scalping.model.ScalpingStrategySettings;
import com.chicu.aibot.strategy.scalping.repository.ScalpingStrategySettingsRepository;
import com.chicu.aibot.strategy.scalping.service.ScalpingStrategySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ScalpingStrategySettingsServiceImpl implements ScalpingStrategySettingsService {

    private final ScalpingStrategySettingsRepository repo;

    @Override
    public ScalpingStrategySettings getOrCreate(Long chatId) {
        return repo.findById(chatId).orElseGet(() -> {
            ScalpingStrategySettings s = ScalpingStrategySettings.builder()
                .chatId(chatId)
                .symbol("BTCUSDT")
                .windowSize(10)
                .priceChangeThreshold(0.5)
                .orderVolume(100.0)
                .spreadThreshold(0.1)
                .takeProfitPct(1.5)
                .stopLossPct(1.0)
                .timeframe("1m")
                .cachedCandlesLimit(200)
                .active(false)
                .build();
            return repo.save(s);
        });
    }

    @Override
    public void save(ScalpingStrategySettings settings) {
        repo.save(settings);
    }
}
