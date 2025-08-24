package com.chicu.aibot.bot.menu.feature.ai.strategy.fibonacciGrid;

import com.chicu.aibot.bot.menu.core.MenuSessionService;
import com.chicu.aibot.bot.menu.core.MenuState;
import com.chicu.aibot.bot.menu.feature.ai.strategy.fibonacciGrid.service.impl.FibonacciGridPanelRendererImpl;
import com.chicu.aibot.bot.menu.feature.common.AiSelectSymbolState;
import com.chicu.aibot.bot.ui.UiAutorefreshService;
import com.chicu.aibot.strategy.fibonacci.model.FibonacciGridStrategySettings;
import com.chicu.aibot.strategy.fibonacci.service.FibonacciGridStrategySettingsService;
import com.chicu.aibot.trading.scheduler.SchedulerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
public class FibonacciGridConfigState implements MenuState {

    public static final String NAME = "ai_trading_fibonacci_config";

    private final FibonacciGridStrategySettingsService settingsService;
    private final MenuSessionService sessionService;
    private final SchedulerService schedulerService;
    private final com.chicu.aibot.bot.menu.feature.ai.strategy.fibonacciGrid.service.FibonacciGridPanelRenderer panelRenderer;
    private final UiAutorefreshService uiAutorefresh;

    @Override
    public String name() { return NAME; }

    @Override
    public SendMessage render(Long chatId) {
        // включаем автообновление именно для ЭТОГО экрана
        uiAutorefresh.enable(chatId, NAME);
        return panelRenderer.render(chatId);
    }

    @Override
    public String handleInput(Update update) {
        if (!update.hasCallbackQuery()) return NAME;

        String data  = update.getCallbackQuery().getData();
        Long chatId  = update.getCallbackQuery().getMessage().getChatId();

        // ручной refresh
        if (FibonacciGridPanelRendererImpl.BTN_REFRESH.equals(data)) {
            return NAME;
        }

        // тумблер запуска/остановки
        if (FibonacciGridPanelRendererImpl.BTN_TOGGLE_ACTIVE.equals(data)) {
            FibonacciGridStrategySettings s = settingsService.getOrCreate(chatId);
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
        if ("ai_trading".equals(data)) {
            uiAutorefresh.disable(chatId, NAME);
            return "ai_trading";
        }

        // выбор пары — отключаем автообновление и уходим в выбор символа
        if (FibonacciGridPanelRendererImpl.BTN_EDIT_SYMBOL.equals(data)) {
            uiAutorefresh.disable(chatId, NAME);
            sessionService.setEditingField(chatId, "symbol");
            sessionService.setReturnState(chatId, NAME);
            return AiSelectSymbolState.NAME;
        }

        // help на отдельный экран
        if (FibonacciGridPanelRendererImpl.BTN_HELP.equals(data)) {
            uiAutorefresh.disable(chatId, NAME);
            return FibonacciGridHelpState.NAME;
        }

        // редактирование числовых полей / таймфрейма — отдельное состояние
        if (data.startsWith("fib_edit_")) {
            uiAutorefresh.disable(chatId, NAME);
            String field = data.substring("fib_edit_".length());
            sessionService.setEditingField(chatId, field);
            return FibonacciGridAdjustState.NAME;
        }

        // переключатели Long/Short — мгновенно
        if (FibonacciGridPanelRendererImpl.BTN_TOGGLE_LONG.equals(data)) {
            FibonacciGridStrategySettings s = settingsService.getOrCreate(chatId);
            s.setAllowLong(!Boolean.TRUE.equals(s.getAllowLong()));
            settingsService.save(s);
            return NAME;
        }
        if (FibonacciGridPanelRendererImpl.BTN_TOGGLE_SHORT.equals(data)) {
            FibonacciGridStrategySettings s = settingsService.getOrCreate(chatId);
            s.setAllowShort(!Boolean.TRUE.equals(s.getAllowShort()));
            settingsService.save(s);
            return NAME;
        }

        return NAME;
    }
}
