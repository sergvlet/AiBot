// src/main/java/com/chicu/aibot/bot/strategy/AiTradingEngine.java
package com.chicu.aibot.strategy;

import com.chicu.aibot.strategy.model.AiTradingSettings;
import com.chicu.aibot.strategy.service.AiTradingSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiTradingEngine {

    private final AiTradingSettingsService settingsService;
    private final StrategyRouter router;

    /** Пользователь нажал «Запустить» */
    public void startBot(Long chatId) {
        AiTradingSettings cfg = settingsService.getOrCreate(chatId);
        cfg.getSelectedStrategies()
                .forEach(strategy -> router.get(strategy).start(chatId));
    }

    /** Пользователь нажал «Стоп» */
    public void stopBot(Long chatId) {
        AiTradingSettings cfg = settingsService.get(chatId);
        cfg.getSelectedStrategies()
                .forEach(strategy -> router.get(strategy).stop(chatId));
    }

    /** По каждому ценовому обновлению */
    public void onPrice(Long chatId, double price) {
        AiTradingSettings cfg = settingsService.getOrCreate(chatId);
        cfg.getSelectedStrategies()
                .forEach(strategy -> router.get(strategy).onPriceUpdate(chatId, price));
    }
}
