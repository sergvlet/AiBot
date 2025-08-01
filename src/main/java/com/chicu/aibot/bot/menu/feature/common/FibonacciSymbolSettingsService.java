// src/main/java/com/chicu/aibot/bot/menu/feature/common/FibonacciSymbolSettingsService.java
package com.chicu.aibot.bot.menu.feature.common;

import com.chicu.aibot.strategy.fibonacci.model.FibonacciGridStrategySettings;
import com.chicu.aibot.strategy.fibonacci.service.FibonacciGridStrategySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service("FIBONACCI_SYMBOL")
@RequiredArgsConstructor
public class FibonacciSymbolSettingsService implements SymbolSettingsService {

    private final FibonacciGridStrategySettingsService svc;

    @Override
    public Object getOrCreate(Long chatId) {
        return svc.getOrCreate(chatId);
    }

    @Override
    public void saveSymbol(Long chatId, Object settings, String symbol) {
        FibonacciGridStrategySettings s = (FibonacciGridStrategySettings) settings;
        s.setSymbol(symbol);
        svc.save(s);
    }

    /**
     * Должно совпадать с константой NAME в вашем FibonacciConfigState.
     */
    @Override
    public String getReturnState() {
        return "ai_trading_fibonacci_config";
    }
}
