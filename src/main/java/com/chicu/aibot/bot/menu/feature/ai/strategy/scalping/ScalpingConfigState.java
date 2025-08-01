package com.chicu.aibot.bot.menu.feature.ai.strategy.scalping;

import com.chicu.aibot.bot.menu.core.MenuSessionService;
import com.chicu.aibot.bot.menu.core.MenuState;
import com.chicu.aibot.bot.menu.feature.common.AiSelectSymbolState;
import com.chicu.aibot.strategy.scalping.model.ScalpingStrategySettings;
import com.chicu.aibot.strategy.scalping.service.ScalpingStrategySettingsService;
import com.chicu.aibot.trading.scheduler.SchedulerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ScalpingConfigState implements MenuState {

    public static final String NAME = "ai_trading_scalping_config";

    private final ScalpingStrategySettingsService settingsService;
    private final MenuSessionService sessionService;
    private final SchedulerService schedulerService;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public SendMessage render(Long chatId) {
        ScalpingStrategySettings s = settingsService.getOrCreate(chatId);

        String text = """
                *📊 Scalping Strategy*

                *Текущие параметры:*
                • Символ: `%s`
                • Объем: `%.4f`
                • Таймфрейм: `%s`
                • История: `%d` свечей
                • Окно: `%d` свечей
                • ΔЦены: `%.2f%%`
                • Макс. спред: `%.2f%%`
                • TP: `%.2f%%` • SL: `%.2f%%`
                • Статус: *%s*
                """.formatted(
                s.getSymbol(),
                s.getOrderVolume(),
                s.getTimeframe(),
                s.getCachedCandlesLimit(),
                s.getWindowSize(),
                s.getPriceChangeThreshold(),
                s.getSpreadThreshold(),
                s.getTakeProfitPct(),
                s.getStopLossPct(),
                s.isActive() ? "🟢 Запущена" : "🔴 Остановлена"
        );

        List<List<InlineKeyboardButton>> rows = new ArrayList<>(List.of(
                List.of(button("✏️ Пара", "edit_symbol"), button("✏️ Объем", "scalp_edit_orderVolume"), button("✏️ История", "scalp_edit_cachedCandlesLimit")),
                List.of(button("✏️ Окно", "scalp_edit_windowSize"), button("✏️ ΔЦены", "scalp_edit_priceChangeThreshold"), button("✏️ Макс. спред", "scalp_edit_spreadThreshold")),
                List.of(button("✏️ TP", "scalp_edit_takeProfitPct"), button("✏️ SL", "scalp_edit_stopLossPct"), button("‹ Назад", "ai_trading")),
                List.of(button(s.isActive() ? "🛑 Остановить стратегию" : "▶️ Запустить стратегию", "scalp_toggle_active"))
        ));

        return SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode("Markdown")
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                .build();
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

    private InlineKeyboardButton button(String text, String data) {
        return InlineKeyboardButton.builder().text(text).callbackData(data).build();
    }
}
