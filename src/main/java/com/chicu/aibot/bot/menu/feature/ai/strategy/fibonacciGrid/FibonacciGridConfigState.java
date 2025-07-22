package com.chicu.aibot.bot.menu.feature.ai.strategy.fibonacciGrid;

import com.chicu.aibot.bot.menu.core.MenuSessionService;
import com.chicu.aibot.bot.menu.core.MenuState;
import com.chicu.aibot.strategy.fibonacci.model.FibonacciGridStrategySettings;
import com.chicu.aibot.strategy.fibonacci.service.FibonacciGridStrategySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

@Component
@RequiredArgsConstructor
public class FibonacciGridConfigState implements MenuState {
    public static final String NAME = "ai_trading_fibonacci_config";

    private final FibonacciGridStrategySettingsService service;
    private final MenuSessionService sessionService;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public SendMessage render(Long chatId) {
        FibonacciGridStrategySettings s = service.getOrCreate(chatId);

        String text =
                "*🔶 Fibonacci Grid Strategy*\n\n" +
                        "Сеточная стратегия по ключевым уровням Фибоначчи.\n\n" +
                        "*Текущие параметры:*\n" +
                        "• Символ: `" + s.getSymbol() + "` — торговая пара\n" +
                        "• Уровни: `" + s.getLevels() + "` — куда ставить ордера\n" +
                        "• Шаг: `" + String.format("%.2f", s.getGridSizePct()) + "%` — расстояние между ордерами\n" +
                        "• Объем: `" + String.format("%.4f", s.getOrderVolume()) + "` — размер каждого ордера\n" +
                        "• Макс. ордеров: `" + s.getMaxActiveOrders() + "` — одновременно открыто\n" +
                        "• TP: `" + String.format("%.2f", s.getTakeProfitPct()) + "%` — профит от средней цены\n" +
                        "• SL: `" + String.format("%.2f", s.getStopLossPct()) + "%` — защита по просадке\n" +
                        "• Short: `" + (s.getAllowShort() ? "✅" : "❌") + "` • Long: `" + (s.getAllowLong() ? "✅" : "❌") + "`\n" +
                        "• Таймфрейм: `" + s.getTimeframe() + "` • История: `" + s.getCachedCandlesLimit() + "` свечей\n";

        // Кнопки по 3 в ряд
        List<List<InlineKeyboardButton>> rows = List.of(
                List.of(
                        InlineKeyboardButton.builder().text("✏️ Символ").callbackData("fibo_edit_symbol").build(),
                        InlineKeyboardButton.builder().text("✏️ Уровни").callbackData("fibo_edit_levels").build(),
                        InlineKeyboardButton.builder().text("✏️ Шаг").callbackData("fibo_edit_gridSizePct").build()
                ),
                List.of(
                        InlineKeyboardButton.builder().text("✏️ Объем").callbackData("fibo_edit_orderVolume").build(),
                        InlineKeyboardButton.builder().text("✏️ Макс ордеры").callbackData("fibo_edit_maxActiveOrders").build(),
                        InlineKeyboardButton.builder().text("✏️ Take-Profit").callbackData("fibo_edit_takeProfitPct").build()
                ),
                List.of(
                        InlineKeyboardButton.builder().text("✏️ Stop-Loss").callbackData("fibo_edit_stopLossPct").build(),
                        InlineKeyboardButton.builder().text("⚙️ Toggle Short").callbackData("fibo_edit_allowShort").build(),
                        InlineKeyboardButton.builder().text("⚙️ Toggle Long").callbackData("fibo_edit_allowLong").build()
                ),
                List.of(
                        InlineKeyboardButton.builder().text("✏️ Таймфрейм").callbackData("fibo_edit_timeframe").build(),
                        InlineKeyboardButton.builder().text("✏️ История").callbackData("fibo_edit_cachedCandlesLimit").build(),
                        InlineKeyboardButton.builder().text("‹ Назад").callbackData("ai_trading").build()
                )

        );

        return SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode("Markdown")
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                .build();
    }

    @Override
    public String handleInput(Update update) {
        if (!update.hasCallbackQuery()) {
            return NAME;
        }
        String data = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();

        // Если нажали «✏️ …» или «⚙️ …» — сохраняем в сессии поле и переходим в AdjustState
        if (data.startsWith("fibo_edit_")) {
            String field = data.substring("fibo_edit_".length());
            sessionService.setEditingField(chatId, field);
            return FibonacciGridAdjustState.NAME;
        }

        // «‹ Назад»
        if ("ai_trading".equals(data)) {
            return "ai_trading";
        }

        // Любое другое — остаёмся
        return NAME;
    }
}
