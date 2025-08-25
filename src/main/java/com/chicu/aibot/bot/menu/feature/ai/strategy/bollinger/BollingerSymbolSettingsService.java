package com.chicu.aibot.bot.menu.feature.ai.strategy.bollinger;

import com.chicu.aibot.bot.menu.feature.common.SymbolSettingsService;
import com.chicu.aibot.strategy.bollinger.model.BollingerStrategySettings;
import com.chicu.aibot.strategy.bollinger.service.BollingerStrategySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service("bollingerSymbolSettingsService")
@RequiredArgsConstructor
public class BollingerSymbolSettingsService implements SymbolSettingsService {

    private final BollingerStrategySettingsService settingsService;

    @Override
    public String getReturnState() {
        return BollingerConfigState.NAME;
    }

    @Override
    public Object getOrCreate(Long chatId) {
        return settingsService.getOrCreate(chatId);
    }

    @Override
    public void saveSymbol(Long chatId, Object settings, String symbol) {
        BollingerStrategySettings s = (BollingerStrategySettings) settings;
        s.setSymbol(symbol);
        settingsService.save(s);
    }
}
