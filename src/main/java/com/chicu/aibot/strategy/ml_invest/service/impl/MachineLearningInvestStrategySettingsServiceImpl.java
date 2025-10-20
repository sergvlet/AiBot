package com.chicu.aibot.strategy.ml_invest.service.impl;

import com.chicu.aibot.strategy.ml_invest.model.MachineLearningInvestStrategySettings;
import com.chicu.aibot.strategy.ml_invest.repository.MachineLearningInvestStrategySettingsRepository;
import com.chicu.aibot.strategy.ml_invest.service.MachineLearningInvestStrategySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class MachineLearningInvestStrategySettingsServiceImpl implements MachineLearningInvestStrategySettingsService {

    private final MachineLearningInvestStrategySettingsRepository repo;

    @Override
    public MachineLearningInvestStrategySettings getOrCreate(Long chatId) {
        return repo.findByChatId(chatId).orElseGet(() -> {
            MachineLearningInvestStrategySettings s = MachineLearningInvestStrategySettings.builder()
                    .modelPath("ml_models/xgboost_btc.joblib")
                    .timeframe("15m")
                    .cachedCandlesLimit(200)
                    .buyThreshold(new BigDecimal("0.60"))
                    .sellThreshold(new BigDecimal("0.60"))
                    .takeProfitPct(new BigDecimal("2.0"))
                    .stopLossPct(new BigDecimal("1.0"))
                    .symbol("BTCUSDT")
                    .active(false)
                    .build();
            // ВАЖНО: chatId — поле из базового StrategySettings, у билдера его нет
            s.setChatId(chatId);
            return repo.save(s);
        });
    }

    @Override
    public MachineLearningInvestStrategySettings save(MachineLearningInvestStrategySettings settings) {
        return repo.save(settings);
    }
}
