package com.chicu.aibot.bot.menu.feature.ai.strategy.bollinger;

import com.chicu.aibot.bot.menu.core.MenuSessionService;
import com.chicu.aibot.bot.menu.core.MenuState;
import com.chicu.aibot.bot.menu.feature.ai.strategy.bollinger.service.BollingerPanelRenderer;
import com.chicu.aibot.bot.menu.feature.ai.strategy.bollinger.service.impl.BollingerPanelRendererImpl;
import com.chicu.aibot.bot.menu.feature.common.AiSelectSymbolState;
import com.chicu.aibot.bot.ui.UiAutorefreshService;
import com.chicu.aibot.strategy.bollinger.model.BollingerStrategySettings;
import com.chicu.aibot.strategy.bollinger.service.BollingerStrategySettingsService;
import com.chicu.aibot.trading.scheduler.SchedulerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
@Slf4j
public class BollingerConfigState implements MenuState {

    public static final String NAME = "ai_trading_bollinger_config";

    private final BollingerStrategySettingsService settingsService;
    private final MenuSessionService sessionService;
    private final SchedulerService schedulerService;
    private final BollingerPanelRenderer panelRenderer;
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

        final String data  = update.getCallbackQuery().getData();
        final Long chatId  = update.getCallbackQuery().getMessage().getChatId();
        if (data == null) return NAME;

        // ручной refresh
        if (BollingerPanelRendererImpl.BTN_REFRESH.equals(data)) {
            return NAME;
        }

        // запуск/остановка стратегии
        if (BollingerPanelRendererImpl.BTN_TOGGLE_ACTIVE.equals(data)) {
            BollingerStrategySettings s = settingsService.getOrCreate(chatId);
            s.setActive(!s.isActive());
            if (s.isActive()) {
                schedulerService.startStrategy(chatId, s.getType().name());
            } else {
                schedulerService.stopStrategy(chatId, s.getType().name());
            }
            settingsService.save(s);
            return NAME;
        }

        // назад в AI-меню — при уходе выключаем автообновление
        if ("ai_trading".equals(data)) {
            uiAutorefresh.disable(chatId, NAME);
            return "ai_trading";
        }

        // выбор символа — выключаем автообновление и переходим в селектор
        if (BollingerPanelRendererImpl.BTN_EDIT_SYMBOL.equals(data)) {
            uiAutorefresh.disable(chatId, NAME);
            sessionService.setEditingField(chatId, "symbol");
            sessionService.setReturnState(chatId, NAME);
            return AiSelectSymbolState.NAME;
        }

        // help — отдельный экран
        if (BollingerPanelRendererImpl.BTN_HELP.equals(data)) {
            uiAutorefresh.disable(chatId, NAME);
            return "ai_trading_bollinger_help";
        }

        // тумблеры Long/Short — переключаем мгновенно, без сравнения с null
        if (BollingerPanelRendererImpl.BTN_TOGGLE_LONG.equals(data)) {
            BollingerStrategySettings s = settingsService.getOrCreate(chatId);
            s.setAllowLong(!Boolean.TRUE.equals(s.getAllowLong()));
            settingsService.save(s);
            return NAME;
        }
        if (BollingerPanelRendererImpl.BTN_TOGGLE_SHORT.equals(data)) {
            BollingerStrategySettings s = settingsService.getOrCreate(chatId);
            s.setAllowShort(!Boolean.TRUE.equals(s.getAllowShort()));
            settingsService.save(s);
            return NAME;
        }

        // редактирование числовых полей / ТФ — отдельное состояние
        if (data.startsWith("boll_edit_")) {
            uiAutorefresh.disable(chatId, NAME);
            String field = data.substring("boll_edit_".length());
            sessionService.setEditingField(chatId, field);
            return "ai_trading_bollinger_adjust";
        }

        return NAME;
    }
}
