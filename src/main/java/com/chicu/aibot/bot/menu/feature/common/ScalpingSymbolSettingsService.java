// src/main/java/com/chicu/aibot/bot/menu/feature/common/ScalpingSymbolSettingsService.java
package com.chicu.aibot.bot.menu.feature.common;

import com.chicu.aibot.strategy.scalping.model.ScalpingStrategySettings;
import com.chicu.aibot.strategy.scalping.service.ScalpingStrategySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service("SCALPING_SYMBOL")
@RequiredArgsConstructor
public class ScalpingSymbolSettingsService implements SymbolSettingsService {

    private final ScalpingStrategySettingsService svc;

    @Override
    public Object getOrCreate(Long chatId) {
        return svc.getOrCreate(chatId);
    }

    @Override
    public void saveSymbol(Long chatId, Object settings, String symbol) {
        ScalpingStrategySettings s = (ScalpingStrategySettings) settings;
        s.setSymbol(symbol);
        svc.save(s);  // сохраняем новые настройки
    }

    /**
     * Название состояния, в которое мы вернёмся после выбора пары.
     * Должно совпадать с константой NAME в вашем ScalpingConfigState.
     */
    @Override
    public String getReturnState() {
        return "ai_trading_scalping_config";
    }
}
