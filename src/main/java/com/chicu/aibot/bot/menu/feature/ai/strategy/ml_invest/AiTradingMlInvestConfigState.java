package com.chicu.aibot.bot.menu.feature.ai.strategy.ml_invest;

import com.chicu.aibot.bot.menu.core.MenuSessionService;
import com.chicu.aibot.bot.menu.core.MenuState;
import com.chicu.aibot.bot.menu.feature.ai.strategy.ml_invest.service.MlInvestPanelRenderer;
import com.chicu.aibot.bot.menu.feature.ai.strategy.ml_invest.service.impl.MlInvestPanelRendererImpl;
import com.chicu.aibot.bot.menu.feature.common.AiSelectSymbolState;
import com.chicu.aibot.bot.ui.UiAutorefreshService;
import com.chicu.aibot.strategy.ml_invest.model.MachineLearningInvestStrategySettings;
import com.chicu.aibot.strategy.ml_invest.service.MachineLearningInvestStrategySettingsService;
import com.chicu.aibot.trading.scheduler.SchedulerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

@Slf4j
@Component("ai_trading_ml_invest_config")
@RequiredArgsConstructor
public class AiTradingMlInvestConfigState implements MenuState {

    public static final String NAME = "ai_trading_ml_invest_config";

    private final MachineLearningInvestStrategySettingsService settingsService;
    private final MenuSessionService sessionService;
    private final SchedulerService schedulerService;
    private final MlInvestPanelRenderer panelRenderer;
    private final UiAutorefreshService uiAutorefresh;

    @Override public String name() { return NAME; }

    @Override
    public SendMessage render(Long chatId) {
        uiAutorefresh.enable(chatId, NAME);
        return panelRenderer.render(chatId);
    }

    // удобная обёртка
    public SendMessage render(Update update) {
        return render(extractChatId(update));
    }

    @Override
    public String handleInput(Update update) {
        if (update == null || !update.hasCallbackQuery()) return NAME;

        final String data  = update.getCallbackQuery().getData();
        final Long chatId  = update.getCallbackQuery().getMessage().getChatId();

        if (MlInvestPanelRendererImpl.BTN_REFRESH.equals(data)) {
            return NAME;
        }

        if (MlInvestPanelRendererImpl.BTN_TOGGLE_ACTIVE.equals(data)) {
            MachineLearningInvestStrategySettings s = settingsService.getOrCreate(chatId);
            s.setActive(!s.isActive());
            if (s.isActive()) {
                schedulerService.startStrategy(chatId, s.getType().name());
            } else {
                schedulerService.stopStrategy(chatId, s.getType().name());
            }
            settingsService.save(s);
            return NAME;
        }

        if ("ai_trading".equals(data)) {
            uiAutorefresh.disable(chatId, NAME);
            return "ai_trading";
        }

        if (MlInvestPanelRendererImpl.BTN_EDIT_SYMBOL.equals(data)) {
            uiAutorefresh.disable(chatId, NAME);
            sessionService.setEditingField(chatId, "symbol");
            sessionService.setReturnState(chatId, NAME);
            return AiSelectSymbolState.NAME;
        }

        if (MlInvestPanelRendererImpl.BTN_HELP.equals(data)) {
            uiAutorefresh.disable(chatId, NAME);
            return MlInvestPanelRendererImpl.BTN_HELP; // "ai_trading_ml_invest_help"
        }

        // редактирование полей (включая volumeMode)
        if (data.startsWith("ml_edit_")) {
            uiAutorefresh.disable(chatId, NAME);
            String field = data.substring("ml_edit_".length());
            sessionService.setEditingField(chatId, field);
            return AiTradingMlInvestAdjustState.NAME;
        }

        return NAME;
    }

    private long extractChatId(Update u) {
        if (u != null) {
            if (u.hasCallbackQuery() && u.getCallbackQuery().getMessage() != null) return u.getCallbackQuery().getMessage().getChatId();
            if (u.hasMessage() && u.getMessage().getChatId() != null) return u.getMessage().getChatId();
        }
        return 0L;
    }
}
