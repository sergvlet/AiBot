package com.chicu.aibot.bot.menu.feature.ai.strategy.bollinger;

import com.chicu.aibot.bot.menu.core.MenuSessionService;
import com.chicu.aibot.bot.menu.core.MenuState;
import com.chicu.aibot.bot.menu.feature.ai.strategy.AiSelectStrategyState;
import com.chicu.aibot.bot.menu.feature.ai.strategy.bollinger.service.BollingerPanelRenderer;
import com.chicu.aibot.bot.menu.feature.common.AiSelectSymbolState;
import com.chicu.aibot.bot.ui.UiAutorefreshService;
import com.chicu.aibot.strategy.bollinger.model.BollingerStrategySettings;
import com.chicu.aibot.strategy.bollinger.service.BollingerStrategySettingsService;
import com.chicu.aibot.trading.scheduler.SchedulerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
public class BollingerConfigState implements MenuState {

    public static final String NAME = "ai_trading_bollinger_config";

    private static final String BTN_REFRESH       = "boll_refresh";
    private static final String BTN_EDIT_SYMBOL   = "boll_edit_symbol";
    private static final String BTN_TOGGLE_ACTIVE = "boll_toggle_active";
    private static final String BTN_TOGGLE_LONG   = "boll_toggle_allow_long";
    private static final String BTN_TOGGLE_SHORT  = "boll_toggle_allow_short";

    private final BollingerStrategySettingsService settingsService;
    private final MenuSessionService sessionService;
    private final SchedulerService schedulerService;
    private final BollingerPanelRenderer panelRenderer;
    private final UiAutorefreshService uiAutorefresh;

    @Override public String name() { return NAME; }

    @Override
    public SendMessage render(Long chatId) {
        // включаем автообновление текущей панели
        uiAutorefresh.enable(chatId, NAME);
        return panelRenderer.render(chatId);
    }

    @Override
    public String handleInput(Update update) {
        if (update == null || !update.hasCallbackQuery()) return NAME;

        String data  = update.getCallbackQuery().getData();
        Long chatId  = update.getCallbackQuery().getMessage().getChatId();

        // ручное обновление панели
        if (BTN_REFRESH.equals(data)) {
            return NAME;
        }

        // запуск/остановка стратегии
        if (BTN_TOGGLE_ACTIVE.equals(data)) {
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

        // ====== 🔥 добавляем переключатели LONG/SHORT ======
        if (BTN_TOGGLE_LONG.equals(data)) {
            BollingerStrategySettings s = settingsService.getOrCreate(chatId);
            s.setAllowLong(!s.getAllowLong());
            settingsService.save(s);
            return NAME;
        }

        if (BTN_TOGGLE_SHORT.equals(data)) {
            BollingerStrategySettings s = settingsService.getOrCreate(chatId);
            s.setAllowShort(!s.getAllowShort());
            settingsService.save(s);
            return NAME;
        }
        // =================================================

        // help-экран
        if (BollingerHelpState.NAME.equals(data)) {
            uiAutorefresh.disable(chatId, NAME);
            return BollingerHelpState.NAME;
        }

        // назад в общий AI-экран
        if ("ai_trading".equals(data)) {
            uiAutorefresh.disable(chatId, NAME);
            return "ai_trading";
        }

        // назад к списку стратегий
        if (AiSelectStrategyState.NAME.equals(data)) {
            uiAutorefresh.disable(chatId, NAME);
            return AiSelectStrategyState.NAME;
        }

        // выбор символа
        if (BTN_EDIT_SYMBOL.equals(data)) {
            uiAutorefresh.disable(chatId, NAME);
            sessionService.setEditingField(chatId, "symbol");
            sessionService.setReturnState(chatId, NAME);
            return AiSelectSymbolState.NAME;
        }

        // редактирование числовых/прочих параметров (prefix boll_edit_)
        if (data.startsWith("boll_edit_")) {
            uiAutorefresh.disable(chatId, NAME);
            String field = data.substring("boll_edit_".length());
            sessionService.setEditingField(chatId, field);
            return BollingerAdjustState.NAME;
        }

        return NAME;
    }
}
