package com.chicu.aibot.bot.menu.feature.ai.strategy.fibonacci;

import com.chicu.aibot.bot.menu.core.MenuSessionService;
import com.chicu.aibot.bot.menu.core.MenuState;
import com.chicu.aibot.bot.menu.feature.common.AiSelectSymbolState;
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
public class FibonacciGridHelpState implements MenuState {

    public static final String NAME = "ai_trading_fibonacci_help";

    private static final String BTN_BACK_CONFIG   = FibonacciGridConfigState.NAME;
    private static final String BTN_TO_SYMBOL     = "fib_help_goto_symbol";
    private static final String BTN_PRESET_CONS   = "fib_help_preset_conservative";
    private static final String BTN_PRESET_BAL    = "fib_help_preset_balanced";
    private static final String BTN_PRESET_AGG    = "fib_help_preset_aggressive";
    private static final String BTN_RESET_DEFAULT = "fib_help_reset_defaults";

    private final FibonacciGridStrategySettingsService settingsService;
    private final MenuSessionService sessionService;

    @Override public String name() { return NAME; }

    @Override
    public SendMessage render(Long chatId) {
        String text = """
                *ℹ️ Fibonacci Grid — справка и быстрые пресеты*

                Стратегия строит уровни вокруг текущей цены через *«Шаг сетки, %»* и ставит лимитные заявки (LONG/SHORT — по разрешённым сторонам). После входа выставляются *TP* и, при необходимости, *SL*.\s
               \s
                *Что означают параметры:*
                • *🎯 Символ* — торговая пара (ETHUSDT и т.п.)
                • *💰 Объём, %* — относительный размер одного ордера (к депозиту)
                • *🧱 Шаг сетки, %* — расстояние между уровнями
                • *📊 Макс. ордеров* — одновременно активных лимиток
                • *📈 LONG / 📉 SHORT* — разрешённые стороны
                • *🎯 TP, %* — цель прибыли с позиции
                • *🛡 SL, %* — ограничение убытка
                • *⏱ Таймфрейм* — интервал свечей/пересчёта
                • *История* — сколько свечей используется для анализа

                *Быстрые пресеты (ставят разумные стартовые параметры):*
                • *Conservative* — низкая частота, низкая нагрузка депозита
                • *Balanced* — средняя активность / риск
                • *Aggressive* — чаще сделки, выше нагрузка
               \s""";

        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(
                        btn("🧩 Conservative", BTN_PRESET_CONS),
                        btn("⚖️ Balanced",    BTN_PRESET_BAL),
                        btn("🔥 Aggressive",  BTN_PRESET_AGG)
                ))
                .keyboardRow(List.of(
                        btn("↩️ Сброс к умолчанию", BTN_RESET_DEFAULT)
                ))
                .keyboardRow(List.of(
                        btn("🎯 Выбрать символ…", BTN_TO_SYMBOL)
                ))
                .keyboardRow(List.of(
                        btn("‹ Назад к настройкам", BTN_BACK_CONFIG)
                ))
                .build();

        return SendMessage.builder()
                .chatId(chatId.toString())
                .parseMode("Markdown")
                .disableWebPagePreview(true)
                .text(text)
                .replyMarkup(markup)
                .build();
    }

    @Override
    public String handleInput(Update update) {
        if (update == null || !update.hasCallbackQuery()) return NAME;

        String data  = update.getCallbackQuery().getData();
        Long chatId  = update.getCallbackQuery().getMessage().getChatId();

        if (BTN_BACK_CONFIG.equals(data)) {
            return FibonacciGridConfigState.NAME;
        }
        if (BTN_TO_SYMBOL.equals(data)) {
            sessionService.setEditingField(chatId, "symbol");
            sessionService.setReturnState(chatId, FibonacciGridConfigState.NAME);
            return AiSelectSymbolState.NAME;
        }

        // Пресеты/сброс — СРАЗУ возвращаемся на конфиг-панель, чтобы увидеть изменения
        if (BTN_PRESET_CONS.equals(data)) {
            applyPreset(chatId, Preset.CONSERVATIVE);
            return FibonacciGridConfigState.NAME;
        }
        if (BTN_PRESET_BAL.equals(data)) {
            applyPreset(chatId, Preset.BALANCED);
            return FibonacciGridConfigState.NAME;
        }
        if (BTN_PRESET_AGG.equals(data)) {
            applyPreset(chatId, Preset.AGGRESSIVE);
            return FibonacciGridConfigState.NAME;
        }
        if (BTN_RESET_DEFAULT.equals(data)) {
            resetDefaults(chatId);
            return FibonacciGridConfigState.NAME;
        }

        return NAME;
    }

    private InlineKeyboardButton btn(String text, String data) {
        return InlineKeyboardButton.builder().text(text).callbackData(data).build();
    }

    private enum Preset { CONSERVATIVE, BALANCED, AGGRESSIVE }

    private void applyPreset(Long chatId, Preset p) {
        FibonacciGridStrategySettings s = settingsService.getOrCreate(chatId);
        switch (p) {
            case CONSERVATIVE -> {
                s.setOrderVolume(0.5);
                s.setGridSizePct(0.80);
                s.setMaxActiveOrders(3);
                s.setTakeProfitPct(0.6);
                s.setStopLossPct(0.8);
                s.setAllowLong(true);
                s.setAllowShort(false);
                s.setTimeframe("1m");
                s.setCachedCandlesLimit(500);
            }
            case BALANCED -> {
                s.setOrderVolume(1.0);
                s.setGridSizePct(0.60);
                s.setMaxActiveOrders(5);
                s.setTakeProfitPct(0.8);
                s.setStopLossPct(1.0);
                s.setAllowLong(true);
                s.setAllowShort(true);
                s.setTimeframe("30s");
                s.setCachedCandlesLimit(720);
            }
            case AGGRESSIVE -> {
                s.setOrderVolume(1.5);
                s.setGridSizePct(0.40);
                s.setMaxActiveOrders(8);
                s.setTakeProfitPct(1.0);
                s.setStopLossPct(1.2);
                s.setAllowLong(true);
                s.setAllowShort(true);
                s.setTimeframe("15s");
                s.setCachedCandlesLimit(1000);
            }
        }
        settingsService.save(s);
    }

    private void resetDefaults(Long chatId) {
        FibonacciGridStrategySettings s = settingsService.getOrCreate(chatId);
        s.setOrderVolume(1.0);
        s.setGridSizePct(0.50);
        s.setMaxActiveOrders(5);
        s.setTakeProfitPct(0.8);
        s.setStopLossPct(1.0);
        s.setAllowLong(true);
        s.setAllowShort(true);
        s.setTimeframe("1m");
        s.setCachedCandlesLimit(500);
        settingsService.save(s);
    }
}
