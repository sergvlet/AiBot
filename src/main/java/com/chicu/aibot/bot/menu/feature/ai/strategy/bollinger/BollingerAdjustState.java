package com.chicu.aibot.bot.menu.feature.ai.strategy.bollinger;

import com.chicu.aibot.bot.menu.core.MenuSessionService;
import com.chicu.aibot.bot.menu.core.MenuState;
import com.chicu.aibot.bot.menu.feature.ai.strategy.AiSelectStrategyState;
import com.chicu.aibot.bot.menu.feature.common.AiSelectSymbolState;
import com.chicu.aibot.strategy.bollinger.model.BollingerStrategySettings;
import com.chicu.aibot.strategy.bollinger.service.BollingerStrategySettingsService;
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
public class BollingerAdjustState implements MenuState {

    public static final String NAME = "ai_trading_bollinger_adjust";

    private final BollingerStrategySettingsService settingsService;
    private final MenuSessionService sessionService;
    private final AiSelectStrategyState selectStrategyState;

    private static final Map<String, FieldMeta> META = Map.ofEntries(
            Map.entry("symbol",             new FieldMeta("Символ", "Торговая пара, например BTCUSDT", null)),
            Map.entry("timeframe",          new FieldMeta("Таймфрейм", "Интервал свечей/обновления. Быстрый выбор ниже.", null)),
            Map.entry("cachedCandlesLimit", new FieldMeta("История", "Кол-во свечей для анализа", 10.0)),
            Map.entry("orderVolume",        new FieldMeta("Объём", "Размер рыночного ордера", 1.0)),
            Map.entry("period",             new FieldMeta("Период SMA", "Число свечей для средней", 1.0)),
            Map.entry("stdDevMultiplier",   new FieldMeta("Коэф. σ", "Множитель стандартного отклонения", 0.1)),
            Map.entry("takeProfitPct",      new FieldMeta("TP", "Take-Profit в %", 0.5)),
            Map.entry("stopLossPct",        new FieldMeta("SL", "Stop-Loss в %", 0.5))
    );

    private static final String[] TF_SECONDS = {"1s","3s","5s","10s","15s","30s"};
    private static final String[] TF_MINUTES = {"1m","3m","5m","15m","30m"};
    private static final String[] TF_HOURS   = {"1h","2h","4h","6h","8h","12h"};
    private static final String[] TF_DWM     = {"1d","3d","1w","1M"};

    @Override public String name() { return NAME; }

    @Override
    public SendMessage render(Long chatId) {
        String field = sessionService.getEditingField(chatId);
        if (field == null || !META.containsKey(field)) {
            sessionService.clearEditingField(chatId);
            return selectStrategyState.render(chatId);
        }

        BollingerStrategySettings s = settingsService.getOrCreate(chatId);
        FieldMeta meta = META.get(field);
        String current = getValueAsString(s, field);

        InlineKeyboardMarkup markup = buildKeyboard(field, s);

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

    private InlineKeyboardMarkup buildKeyboard(String field, BollingerStrategySettings s) {
        var builder = InlineKeyboardMarkup.builder();

        if ("timeframe".equals(field)) {
            addTfRow(builder, TF_SECONDS, s.getTimeframe());
            addTfRow(builder, TF_MINUTES, s.getTimeframe());
            addTfRow(builder, TF_HOURS,   s.getTimeframe());
            addTfRow(builder, TF_DWM,     s.getTimeframe());
        } else if ("symbol".equals(field)) {
            builder.keyboardRow(List.of(
                    InlineKeyboardButton.builder().text("🎯 Выбрать символ…").callbackData("edit_symbol").build()
            ));
        } else {
            // универсальные +/- для числовых полей
            builder.keyboardRow(List.of(
                    InlineKeyboardButton.builder().text("➖").callbackData("boll_dec:" + field).build(),
                    InlineKeyboardButton.builder().text("➕").callbackData("boll_inc:" + field).build()
            ));
        }

        builder.keyboardRow(List.of(
                InlineKeyboardButton.builder().text("‹ Назад").callbackData(BollingerConfigState.NAME).build()
        ));
        return builder.build();
    }

    private void addTfRow(InlineKeyboardMarkup.InlineKeyboardMarkupBuilder b, String[] values, String current) {
        List<InlineKeyboardButton> row = new ArrayList<>(values.length);
        for (String tf : values) {
            String title = tf.equalsIgnoreCase(current) ? "✅ " + tf : tf;
            row.add(InlineKeyboardButton.builder().text(title).callbackData("boll_tf:" + tf).build());
        }
        b.keyboardRow(row);
    }

    @Override
    public String handleInput(Update update) {
        if (update == null || !update.hasCallbackQuery()) return NAME;

        String data  = update.getCallbackQuery().getData();
        Long chatId  = update.getCallbackQuery().getMessage().getChatId();

        if ("edit_symbol".equals(data)) {
            sessionService.setEditingField(chatId, "symbol");
            sessionService.setReturnState(chatId, BollingerConfigState.NAME);
            return AiSelectSymbolState.NAME;
        }

        if (data.startsWith("boll_tf:")) {
            String tf = data.substring("boll_tf:".length());
            BollingerStrategySettings s = settingsService.getOrCreate(chatId);
            s.setTimeframe(tf);
            settingsService.save(s);
            sessionService.setEditingField(chatId, "timeframe");
            return NAME;
        }

        if (data.startsWith("boll_inc:") || data.startsWith("boll_dec:")) {
            String field  = data.substring(data.indexOf(':') + 1);
            String action = data.startsWith("boll_inc:") ? "inc" : "dec";
            if (!META.containsKey(field)) return NAME;

            BollingerStrategySettings s = settingsService.getOrCreate(chatId);
            adjustField(s, field, action);
            settingsService.save(s);
            return NAME;
        }

        if (BollingerConfigState.NAME.equals(data)) {
            sessionService.clearEditingField(chatId);
            return BollingerConfigState.NAME;
        }

        return NAME;
    }

    private String getValueAsString(BollingerStrategySettings s, String field) {
        return switch (field) {
            case "symbol"             -> nvl(s.getSymbol());
            case "timeframe"          -> nvl(s.getTimeframe());
            case "cachedCandlesLimit" -> s.getCachedCandlesLimit() == null ? "" : s.getCachedCandlesLimit().toString();
            case "orderVolume"        -> String.format("%.4f", s.getOrderVolume());
            case "period"             -> s.getPeriod() == null ? "" : s.getPeriod().toString();
            case "stdDevMultiplier"   -> String.format("%.2f", s.getStdDevMultiplier());
            case "takeProfitPct"      -> String.format("%.2f%%", s.getTakeProfitPct());
            case "stopLossPct"        -> String.format("%.2f%%", s.getStopLossPct());
            default                   -> "";
        };
    }

    private void adjustField(BollingerStrategySettings s, String field, String action) {
        double sign = "inc".equals(action) ? 1.0 : -1.0;

        switch (field) {
            case "cachedCandlesLimit" -> {
                int v = safeInt(s.getCachedCandlesLimit()) + (int)Math.round(sign * 10.0);
                s.setCachedCandlesLimit(clampInt(v, 1_000_000));
            }
            case "orderVolume" -> {
                double v = s.getOrderVolume() + sign;
                s.setOrderVolume(clampDouble(v, 0.0001, 1_000_000.0));
            }
            case "period" -> {
                int v = safeInt(s.getPeriod()) + (int)Math.round(sign);
                s.setPeriod(clampInt(v, 10_000));
            }
            case "stdDevMultiplier" -> {
                double v = s.getStdDevMultiplier() + sign * 0.1;
                s.setStdDevMultiplier(clampDouble(v, 0.1, 50.0));
            }
            case "takeProfitPct" -> {
                double v = s.getTakeProfitPct() + sign * 0.5;
                s.setTakeProfitPct(clampDouble(v, 0.0, 10_000.0));
            }
            case "stopLossPct" -> {
                double v = s.getStopLossPct() + sign * 0.5;
                s.setStopLossPct(clampDouble(v, 0.0, 10_000.0));
            }
            default -> { /* noop */ }
        }
    }

    // helpers
    private static int safeInt(Integer v) { return v == null ? 0 : v; }
    private static int clampInt(int v, int max) { return Math.max(1, Math.min(max, v)); }
    private static double clampDouble(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
    private static String nvl(String s) { return (s == null || s.isBlank()) ? "—" : s; }

    private record FieldMeta(String label, String description, Double step) {}
}
