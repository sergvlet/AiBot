package com.chicu.aibot.bot.menu.feature.ai.strategy.ml_invest;

import com.chicu.aibot.bot.menu.feature.common.SymbolSettingsService;
import com.chicu.aibot.strategy.ml_invest.model.MachineLearningInvestStrategySettings;
import com.chicu.aibot.strategy.ml_invest.service.MachineLearningInvestStrategySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service("mlInvestSymbolSettingsService")
@RequiredArgsConstructor
public class MlInvestSymbolSettingsService implements SymbolSettingsService {

    private final MachineLearningInvestStrategySettingsService settingsService;

    @Override
    public String getReturnState() {
        return AiTradingMlInvestConfigState.NAME;
    }

    @Override
    public Object getOrCreate(Long chatId) {
        return settingsService.getOrCreate(chatId);
    }

    @Override
    public void saveSymbol(Long chatId, Object settings, String symbol) {
        MachineLearningInvestStrategySettings s = (MachineLearningInvestStrategySettings) settings;
        s.setSymbol(symbol);
        settingsService.save(s);
    }
}
