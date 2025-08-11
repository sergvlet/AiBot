package com.chicu.aibot.bot.menu.feature.ai.strategy.scalping;

import com.chicu.aibot.bot.menu.core.MenuSessionService;
import com.chicu.aibot.bot.menu.core.MenuState;
import com.chicu.aibot.bot.menu.feature.ai.strategy.scalping.service.ScalpingPanelRenderer;
import com.chicu.aibot.bot.menu.feature.common.AiSelectSymbolState;
import com.chicu.aibot.strategy.scalping.model.ScalpingStrategySettings;
import com.chicu.aibot.strategy.scalping.service.ScalpingStrategySettingsService;
import com.chicu.aibot.trading.scheduler.SchedulerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
public class ScalpingConfigState implements MenuState {

    public static final String NAME = "ai_trading_scalping_config";

    private final ScalpingStrategySettingsService settingsService;
    private final MenuSessionService sessionService;
    private final SchedulerService schedulerService;
    private final ScalpingPanelRenderer panelRenderer;

    @Override public String name() { return NAME; }

    @Override
    public SendMessage render(Long chatId) {
        schedulerService.ensureUiAutorefreshEnabled(chatId, NAME);
        return panelRenderer.render(chatId);
    }


    @Override
    public String handleInput(Update update) {
        if (!update.hasCallbackQuery()) return NAME;

        String data = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();

        if (!data.equals("edit_symbol")) {
            if (data.equals("scalp_toggle_active")) {
                ScalpingStrategySettings s = settingsService.getOrCreate(chatId);
                s.setActive(!s.isActive());
                if (s.isActive()) {
                    schedulerService.startStrategy(chatId, s.getType().name());
                } else {
                    schedulerService.stopStrategy(chatId, s.getType().name());
                }
                settingsService.save(s);
                return NAME;
            } else if (data.equals("ai_trading")) {
                return "ai_trading";
            }
        } else {
            sessionService.setEditingField(chatId, "symbol");
            sessionService.setReturnState(chatId, NAME);
            return AiSelectSymbolState.NAME;
        }

        if (data.startsWith("scalp_edit_")) {
            String field = data.substring("scalp_edit_".length());
            sessionService.setEditingField(chatId, field);
            return ScalpingAdjustState.NAME;
        }

        return NAME;
    }
}
