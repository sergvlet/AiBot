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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class FibonacciGridAdjustState implements MenuState {

    public static final String NAME = "ai_trading_fibonacci_adjust";

    private final FibonacciGridStrategySettingsService settingsService;
    private final MenuSessionService sessionService;

    // шаги подобраны под реальные требования
    private static final Map<String, FieldMeta> META = Map.ofEntries(
            Map.entry("symbol",             new FieldMeta("Символ", "Торговая пара, например ETHUSDT", null)),
            Map.entry("orderVolume",        new FieldMeta("Объём ордера", "Размер рыночного/лимитного ордера (в базовой валюте)", 0.10)),
            Map.entry("gridSizePct",        new FieldMeta("Шаг сетки, %", "Процентное расстояние между уровнями сетки", 0.05)),
            Map.entry("maxActiveOrders",    new FieldMeta("Макс. активных ордеров", "Ограничение по одновременно стоящим заявкам", 1.0)),
            Map.entry("takeProfitPct",      new FieldMeta("TP, %", "Процент фиксации прибыли по уровню", 0.25)),
            Map.entry("stopLossPct",        new FieldMeta("SL, %", "Процент ограничения убытка", 0.10)),
            Map.entry("timeframe",          new FieldMeta("Таймфрейм", "Интервал свечей/обновления (быстрый выбор ниже)", null)),
            Map.entry("cachedCandlesLimit", new FieldMeta("История", "Количество свечей для анализа", 10.0))
    );

    private static final String[] TF_SECONDS = {"1s","3s","5s","10s","15s","30s"};
    private static final String[] TF_MINUTES = {"1m","3m","5m","15m","30m"};
    private static final String[] TF_HOURS   = {"1h","2h","4h","6h","8h","12h"};
    private static final String[] TF_DWM     = {"1d","3d","1w","1M"};

    @Override
    public String name() { return NAME; }

    @Override
    public SendMessage render(Long chatId) {
        String field = sessionService.getEditingField(chatId);
        if (field == null || !META.containsKey(field)) {
            sessionService.clearEditingField(chatId);
            // вернёмся на экран конфигурации
            return SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("Нечего редактировать.")
                    .build();
        }

        FibonacciGridStrategySettings s = settingsService.getOrCreate(chatId);
        FieldMeta meta   = META.get(field);
        String current   = getValueAsString(s, field);
        InlineKeyboardMarkup markup = buildKeyboard(field, meta, s);

        String text = String.format(
                "*%s*\n\n%s\n\nТекущее значение: `%s`",
                meta.label(), meta.description(), current
        );

        return SendMessage.builder()
                .chatId(chatId.toString())
                .parseMode("Markdown")
                .text(text)
                .replyMarkup(markup)
                .build();
    }

    private InlineKeyboardMarkup buildKeyboard(String field, FieldMeta meta, FibonacciGridStrategySettings s) {
        InlineKeyboardMarkup.InlineKeyboardMarkupBuilder b = InlineKeyboardMarkup.builder();

        if ("timeframe".equals(field)) {
            addTfRow(b, TF_SECONDS, s.getTimeframe());
            addTfRow(b, TF_MINUTES, s.getTimeframe());
            addTfRow(b, TF_HOURS,   s.getTimeframe());
            addTfRow(b, TF_DWM,     s.getTimeframe());
        } else if (meta.step() != null) {
            b.keyboardRow(List.of(
                    InlineKeyboardButton.builder().text("➖").callbackData("fib_dec:" + field).build(),
                    InlineKeyboardButton.builder().text("➕").callbackData("fib_inc:" + field).build()
            ));
        } else {
            if ("symbol".equals(field)) {
                b.keyboardRow(List.of(
                        InlineKeyboardButton.builder().text("🎯 Выбрать символ…").callbackData("fib_edit_symbol_go").build()
                ));
            }
        }

        b.keyboardRow(List.of(
                InlineKeyboardButton.builder().text("‹ Назад").callbackData(FibonacciGridConfigState.NAME).build()
        ));
        return b.build();
    }

    private void addTfRow(InlineKeyboardMarkup.InlineKeyboardMarkupBuilder b, String[] values, String current) {
        List<InlineKeyboardButton> row = new ArrayList<>(values.length);
        for (String tf : values) {
            String title = tf.equalsIgnoreCase(current) ? ("✅ " + tf) : tf;
            row.add(InlineKeyboardButton.builder().text(title).callbackData("fib_tf:" + tf).build());
        }
        b.keyboardRow(row);
    }

    @Override
    public String handleInput(Update update) {
        if (update == null || !update.hasCallbackQuery()) return NAME;
        String data  = update.getCallbackQuery().getData();
        Long chatId  = update.getCallbackQuery().getMessage().getChatId();

        if ("fib_edit_symbol_go".equals(data)) {
            sessionService.setEditingField(chatId, "symbol");
            sessionService.setReturnState(chatId, FibonacciGridConfigState.NAME);
            return AiSelectSymbolState.NAME;
        }

        if (data.startsWith("fib_tf:")) {
            String tf = data.substring("fib_tf:".length());
            FibonacciGridStrategySettings s = settingsService.getOrCreate(chatId);
            s.setTimeframe(tf);
            settingsService.save(s);
            sessionService.setEditingField(chatId, "timeframe");
            return NAME;
        }

        if (data.startsWith("fib_inc:") || data.startsWith("fib_dec:")) {
            String field  = data.substring(data.indexOf(':') + 1);
            String action = data.startsWith("fib_inc:") ? "inc" : "dec";
            if (!META.containsKey(field)) return NAME;

            FibonacciGridStrategySettings s = settingsService.getOrCreate(chatId);
            adjustField(s, field, action);
            settingsService.save(s);
            return NAME;
        }

        if (FibonacciGridConfigState.NAME.equals(data)) {
            sessionService.clearEditingField(chatId);
            return FibonacciGridConfigState.NAME;
        }

        return NAME;
    }

    private String getValueAsString(FibonacciGridStrategySettings s, String field) {
        return switch (field) {
            case "symbol"             -> nvl(s.getSymbol());
            case "orderVolume"        -> String.format("%.4f", s.getOrderVolume());
            case "gridSizePct"        -> String.format("%.4f%%", s.getGridSizePct());
            case "maxActiveOrders"    -> s.getMaxActiveOrders() == null ? "" : s.getMaxActiveOrders().toString();
            case "takeProfitPct"      -> String.format("%.2f%%", s.getTakeProfitPct());
            case "stopLossPct"        -> String.format("%.2f%%", s.getStopLossPct());
            case "timeframe"          -> nvl(s.getTimeframe());
            case "cachedCandlesLimit" -> s.getCachedCandlesLimit() == null ? "" : s.getCachedCandlesLimit().toString();
            default -> "";
        };
    }

    private void adjustField(FibonacciGridStrategySettings s, String field, String action) {
        FieldMeta meta = META.get(field);
        if (meta == null || meta.step() == null) return;

        double step = meta.step();
        double sign = "inc".equals(action) ? 1.0 : -1.0;

        switch (field) {
            case "orderVolume" -> {
                double v = s.getOrderVolume() + sign * step;
                s.setOrderVolume(clampDouble(v, 0.0001, 1_000_000.0));
            }
            case "gridSizePct" -> {
                double v = s.getGridSizePct() + sign * step;
                s.setGridSizePct(clampDouble(v, 0.0001, 100.0));
            }
            case "maxActiveOrders" -> {
                int v = safeInt(s.getMaxActiveOrders()) + (int)Math.round(sign * step);
                s.setMaxActiveOrders(clampInt(v));
            }
            case "takeProfitPct" -> {
                double v = s.getTakeProfitPct() + sign * step;
                s.setTakeProfitPct(clampDouble(v, 0.0, 10_000.0));
            }
            case "stopLossPct" -> {
                double v = s.getStopLossPct() + sign * step;
                s.setStopLossPct(clampDouble(v, 0.0, 10_000.0));
            }
            case "cachedCandlesLimit" -> {
                int v = safeInt(s.getCachedCandlesLimit()) + (int)Math.round(sign * step);
                s.setCachedCandlesLimit(clampInt(v));
            }
            default -> { /* no-op */ }
        }
    }

    private static int safeInt(Integer v) { return v == null ? 0 : v; }
    private static int clampInt(int v) { return Math.max(1, Math.min(1000000, v)); }
    private static double clampDouble(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
    private static String nvl(String s) { return (s == null || s.isBlank()) ? "—" : s; }

    private record FieldMeta(String label, String description, Double step) {}
}
