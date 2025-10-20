package com.chicu.aibot.bot.menu.feature.ai.strategy.scalping;

import com.chicu.aibot.bot.menu.core.MenuSessionService;
import com.chicu.aibot.bot.menu.core.MenuState;
import com.chicu.aibot.bot.menu.feature.ai.strategy.scalping.service.ScalpingPanelRenderer;
import com.chicu.aibot.bot.menu.feature.ai.strategy.scalping.service.impl.ScalpingPanelRendererImpl;
import com.chicu.aibot.bot.menu.feature.common.AiSelectSymbolState;
import com.chicu.aibot.bot.ui.UiAutorefreshService;
import com.chicu.aibot.strategy.scalping.model.ScalpingStrategySettings;
import com.chicu.aibot.strategy.scalping.service.ScalpingStrategySettingsService;
import com.chicu.aibot.trading.scheduler.SchedulerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScalpingConfigState implements MenuState {

    public static final String NAME = "ai_trading_scalping_config";

    private final ScalpingStrategySettingsService settingsService;
    private final MenuSessionService sessionService;
    private final SchedulerService schedulerService;
    private final ScalpingPanelRenderer panelRenderer;
    private final UiAutorefreshService uiAutorefresh;

    @Override
    public String name() { return NAME; }

    @Override
    public SendMessage render(Long chatId) {
        // включаем автообновление именно для этого экрана
        uiAutorefresh.enable(chatId, NAME);
        return panelRenderer.render(chatId);
    }

    @Override
    public String handleInput(Update update) {
        if (!update.hasCallbackQuery()) return NAME;

        final String data  = update.getCallbackQuery().getData();
        final Long chatId  = update.getCallbackQuery().getMessage().getChatId();
        if (data == null) return NAME;

        // ручной refresh — остаёмся здесь
        switch (data) {
            case ScalpingPanelRendererImpl.BTN_REFRESH -> {
                return NAME;
            }


            // тумблер запуска/остановки
            case ScalpingPanelRendererImpl.BTN_TOGGLE_ACTIVE -> {
                ScalpingStrategySettings s = settingsService.getOrCreate(chatId);
                s.setActive(!s.isActive());
                if (s.isActive()) {
                    schedulerService.startStrategy(chatId, s.getType().name());
                } else {
                    schedulerService.stopStrategy(chatId, s.getType().name());
                }
                settingsService.save(s);
                return NAME;
            }


            // назад в AI-меню — отключаем автообновление
            case "ai_trading" -> {
                uiAutorefresh.disable(chatId, NAME);
                return "ai_trading";
            }


            // выбор символа — отключаем автообновление и переходим в выбор
            case ScalpingPanelRendererImpl.BTN_EDIT_SYMBOL -> {
                uiAutorefresh.disable(chatId, NAME);
                sessionService.setEditingField(chatId, "symbol");
                sessionService.setReturnState(chatId, NAME);
                return AiSelectSymbolState.NAME;
            }


            // help — отдельный экран
            case ScalpingPanelRendererImpl.BTN_HELP -> {
                uiAutorefresh.disable(chatId, NAME);
                return "ai_trading_scalping_help";
            }
        }

        // редактирование числовых полей / ТФ — отдельное состояние
        if (data.startsWith("scalp_edit_")) {
            uiAutorefresh.disable(chatId, NAME);
            String field = data.substring("scalp_edit_".length());
            sessionService.setEditingField(chatId, field);
            return "ai_trading_scalping_adjust";
        }

        return NAME;
    }
}
