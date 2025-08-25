package com.chicu.aibot.bot.menu.feature.ai.strategy.scalping;

import com.chicu.aibot.bot.menu.core.MenuSessionService;
import com.chicu.aibot.bot.menu.core.MenuState;
import com.chicu.aibot.bot.menu.feature.ai.strategy.scalping.service.ScalpingPanelRenderer;
import com.chicu.aibot.bot.menu.feature.common.AiSelectSymbolState;
import com.chicu.aibot.bot.ui.UiAutorefreshService;
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

    // callbacks из панели
    private static final String BTN_EDIT_SYMBOL   = "edit_symbol";
    private static final String BTN_TOGGLE_ACTIVE = "scalp_toggle_active";
    private static final String BTN_REFRESH       = "scalp_refresh";
    private static final String BTN_HELP          = "scalp_help";

    private final ScalpingStrategySettingsService settingsService;
    private final MenuSessionService sessionService;
    private final SchedulerService schedulerService;
    private final ScalpingPanelRenderer panelRenderer;
    private final UiAutorefreshService uiAutorefresh;

    @Override
    public String name() { return NAME; }

    @Override
    public SendMessage render(Long chatId) {
        // Всегда рендерим основную панель и включаем автообновление
        uiAutorefresh.enable(chatId, NAME);
        return panelRenderer.render(chatId);
    }

    @Override
    public String handleInput(Update update) {
        if (!update.hasCallbackQuery()) return NAME;

        String data  = update.getCallbackQuery().getData();
        Long chatId  = update.getCallbackQuery().getMessage().getChatId();

        // Ручное обновление панели — просто остаёмся тут
        if (BTN_REFRESH.equals(data)) return NAME;

        // Запуск/остановка стратегии
        if (BTN_TOGGLE_ACTIVE.equals(data)) {
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

        // Выход в главное AI-меню — отключаем автообновление
        if ("ai_trading".equals(data)) {
            uiAutorefresh.disable(chatId, NAME);
            return "ai_trading";
        }

        // Переход к выбору символа — отключаем автообновление, сохраняем возврат
        if (BTN_EDIT_SYMBOL.equals(data)) {
            uiAutorefresh.disable(chatId, NAME);
            sessionService.setEditingField(chatId, "symbol");
            sessionService.setReturnState(chatId, NAME);
            return AiSelectSymbolState.NAME;
        }

        // Переход к редактированию параметров — отключаем автообновление
        if (data.startsWith("scalp_edit_")) {
            uiAutorefresh.disable(chatId, NAME);
            String field = data.substring("scalp_edit_".length());
            sessionService.setEditingField(chatId, field);
            return ScalpingAdjustState.NAME;
        }

        // Справка/пресеты — отдельное состояние
        if (BTN_HELP.equals(data)) {
            uiAutorefresh.disable(chatId, NAME);
            return ScalpingHelpState.NAME;
        }

        return NAME;
    }
}
