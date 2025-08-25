package com.chicu.aibot.bot.menu.feature.ai.strategy.fibonacci;

import com.chicu.aibot.bot.menu.feature.common.SymbolSettingsService;
import com.chicu.aibot.strategy.fibonacci.model.FibonacciGridStrategySettings;
import com.chicu.aibot.strategy.fibonacci.service.FibonacciGridStrategySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component("fibonacciSymbolSettingsService")
@RequiredArgsConstructor
public class FibonacciSymbolSettingsService implements SymbolSettingsService {

    private final FibonacciGridStrategySettingsService settingsService;

    @Override
    public Object getOrCreate(Long chatId) {
        return settingsService.getOrCreate(chatId);
    }

    @Override
    public void saveSymbol(Long chatId, Object settings, String symbol) {
        FibonacciGridStrategySettings s = (FibonacciGridStrategySettings) settings;
        s.setSymbol(symbol);
        settingsService.save(s);
    }

    @Override
    public String getReturnState() {
        return FibonacciGridConfigState.NAME;
    }


}
