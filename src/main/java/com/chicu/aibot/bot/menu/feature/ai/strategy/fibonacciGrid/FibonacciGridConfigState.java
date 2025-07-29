package com.chicu.aibot.bot.menu.feature.ai.strategy.fibonacciGrid;

import com.chicu.aibot.bot.menu.core.MenuSessionService;
import com.chicu.aibot.bot.menu.core.MenuState;
import com.chicu.aibot.bot.menu.feature.common.AiSelectSymbolState;
import com.chicu.aibot.strategy.fibonacci.model.FibonacciGridStrategySettings;
import com.chicu.aibot.strategy.fibonacci.service.FibonacciGridStrategySettingsService;
import com.chicu.aibot.trading.core.SchedulerService;
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
public class FibonacciGridConfigState implements MenuState {

    public static final String NAME = "ai_trading_fibonacci_config";

    private final FibonacciGridStrategySettingsService service;
    private final MenuSessionService sessionService;
    private final SchedulerService schedulerService;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public SendMessage render(Long chatId) {
        FibonacciGridStrategySettings s = service.getOrCreate(chatId);

        String text = """
                *🔶 Fibonacci Grid Strategy*

                Сеточная стратегия по ключевым уровням Фибоначчи.

                *Текущие параметры:*
                • Символ: `%s`
                • Уровни: `%s`
                • Шаг: `%.2f%%`
                • Объем: `%.4f`
                • Макс. ордеров: `%d`
                • TP: `%.2f%%`
                • SL: `%.2f%%`
                • Short: `%s` • Long: `%s`
                • Таймфрейм: `%s`
                • История: `%d` свечей
                • Статус: *%s*
                """.formatted(
                s.getSymbol(),
                s.getLevels(),
                s.getGridSizePct(),
                s.getOrderVolume(),
                s.getMaxActiveOrders(),
                s.getTakeProfitPct(),
                s.getStopLossPct(),
                s.getAllowShort() ? "✅" : "❌",
                s.getAllowLong() ? "✅" : "❌",
                s.getTimeframe(),
                s.getCachedCandlesLimit(),
                s.isActive() ? "🟢 Запущена" : "🔴 Остановлена"
        );

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                button("✏️ Пара", "edit_symbol"),
                button("✏️ Уровни", "fibo_edit_levels"),
                button("✏️ Шаг", "fibo_edit_gridSizePct")
        ));
        rows.add(List.of(
                button("✏️ Объем", "fibo_edit_orderVolume"),
                button("✏️ Макс ордеры", "fibo_edit_maxActiveOrders"),
                button("✏️ Take-Profit", "fibo_edit_takeProfitPct")
        ));
        rows.add(List.of(
                button("✏️ Stop-Loss", "fibo_edit_stopLossPct"),
                button("⚙️ Toggle Short", "fibo_edit_allowShort"),
                button("⚙️ Toggle Long", "fibo_edit_allowLong")
        ));
        rows.add(List.of(
                button("✏️ Таймфрейм", "fibo_edit_timeframe"),
                button("✏️ История", "fibo_edit_cachedCandlesLimit"),
                button("‹ Назад", "ai_trading")
        ));
        rows.add(List.of(
                InlineKeyboardButton.builder()
                        .text(s.isActive() ? "🛑 Остановить стратегию" : "▶️ Запустить стратегию")
                        .callbackData("fibo_toggle_active")
                        .build()
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

        if ("edit_symbol".equals(data)) {
            sessionService.setEditingField(chatId, "symbol");
            sessionService.setReturnState(chatId, NAME);
            return AiSelectSymbolState.NAME;
        }

        if (data.startsWith("fibo_edit_")) {
            sessionService.setEditingField(chatId, data.substring("fibo_edit_".length()));
            return FibonacciGridAdjustState.NAME;
        }

        if ("fibo_toggle_active".equals(data)) {
            FibonacciGridStrategySettings s = service.getOrCreate(chatId);
            boolean active = !s.isActive();
            s.setActive(active);
            if (active) {
                schedulerService.startStrategy(chatId, s.getType().name());
            } else {
                schedulerService.stopStrategy(chatId, s.getType().name());
            }
            service.save(s);
            return NAME;
        }

        if ("ai_trading".equals(data)) {
            return "ai_trading";
        }

        return NAME;
    }

    private InlineKeyboardButton button(String text, String data) {
        return InlineKeyboardButton.builder().text(text).callbackData(data).build();
    }
}
