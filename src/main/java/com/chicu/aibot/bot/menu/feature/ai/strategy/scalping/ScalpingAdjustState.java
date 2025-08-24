package com.chicu.aibot.bot.menu.feature.ai.strategy.scalping;

import com.chicu.aibot.bot.menu.core.MenuSessionService;
import com.chicu.aibot.bot.menu.core.MenuState;
import com.chicu.aibot.bot.menu.feature.ai.strategy.AiSelectStrategyState;
import com.chicu.aibot.bot.menu.feature.common.AiSelectSymbolState;
import com.chicu.aibot.strategy.scalping.model.ScalpingStrategySettings;
import com.chicu.aibot.strategy.scalping.service.ScalpingStrategySettingsService;
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
public class ScalpingAdjustState implements MenuState {

    public static final String NAME = "ai_trading_scalping_adjust";

    private final ScalpingStrategySettingsService settingsService;
    private final MenuSessionService sessionService;
    private final AiSelectStrategyState selectStrategyState;

    /**
     * Step == null означает, что поле не инкрементируется +/- (правим на другом экране/через пресеты).
     */
    private static final Map<String, FieldMeta> META = Map.ofEntries(
            Map.entry("symbol",               new FieldMeta("Символ", "Торговая пара, например BTCUSDT", null)),
            Map.entry("windowSize",           new FieldMeta("Окно", "Размер окна (кол-во свечей)", 5.0)),
            Map.entry("priceChangeThreshold", new FieldMeta("Порог движения", "Изменение цены в % для входа", 0.05)),
            Map.entry("spreadThreshold",      new FieldMeta("Спред", "Макс. допустимый спред", 0.01)),
            Map.entry("takeProfitPct",        new FieldMeta("TP", "Take-Profit в %", 0.05)),
            Map.entry("stopLossPct",          new FieldMeta("SL", "Stop-Loss в %", 0.05)),
            Map.entry("orderVolume",          new FieldMeta("Объем", "Объем рыночного ордера", 0.5)),
            Map.entry("timeframe",            new FieldMeta("Таймфрейм", "Интервал обновления/свечей (например 1m). Быстрый выбор ниже.", null)),
            Map.entry("cachedCandlesLimit",   new FieldMeta("История", "Кол-во свечей для анализа", 20.0))
    );

    // пресеты таймфрейма
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
            return selectStrategyState.render(chatId);
        }

        ScalpingStrategySettings s = settingsService.getOrCreate(chatId);
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

    private InlineKeyboardMarkup buildKeyboard(String field, FieldMeta meta, ScalpingStrategySettings s) {
        InlineKeyboardMarkup.InlineKeyboardMarkupBuilder builder = InlineKeyboardMarkup.builder();

        if ("timeframe".equals(field)) {
            // быстрый выбор таймфрейма от 1 секунды и выше
            addTfRow(builder, TF_SECONDS, s.getTimeframe());
            addTfRow(builder, TF_MINUTES, s.getTimeframe());
            addTfRow(builder, TF_HOURS,   s.getTimeframe());
            addTfRow(builder, TF_DWM,     s.getTimeframe());
        } else if (meta.step() != null) {
            // числовые поля: +/- шаг
            builder.keyboardRow(List.of(
                    InlineKeyboardButton.builder().text("➖").callbackData("scalp_dec:" + field).build(),
                    InlineKeyboardButton.builder().text("➕").callbackData("scalp_inc:" + field).build()
            ));
        } else {
            // специальные экраны без шага
            if ("symbol".equals(field)) {
                builder.keyboardRow(List.of(
                        InlineKeyboardButton.builder().text("🎯 Выбрать символ…").callbackData("edit_symbol").build()
                ));
            }
        }

        // назад
        builder.keyboardRow(List.of(
                InlineKeyboardButton.builder()
                        .text("‹ Назад")
                        .callbackData(ScalpingConfigState.NAME)
                        .build()
        ));
        return builder.build();
    }

    private void addTfRow(InlineKeyboardMarkup.InlineKeyboardMarkupBuilder builder, String[] values, String current) {
        List<InlineKeyboardButton> row = new ArrayList<>(values.length);
        for (String tf : values) {
            String title = tf.equalsIgnoreCase(current) ? ("✅ " + tf) : tf;
            row.add(InlineKeyboardButton.builder()
                    .text(title)
                    .callbackData("scalp_tf:" + tf)
                    .build());
        }
        builder.keyboardRow(row);
    }

    @Override
    public String handleInput(Update update) {
        if (update == null || !update.hasCallbackQuery()) return NAME;

        String data  = update.getCallbackQuery().getData();
        Long chatId  = update.getCallbackQuery().getMessage().getChatId();

        // быстрый переход к выбору символа
        if ("edit_symbol".equals(data)) {
            sessionService.setEditingField(chatId, "symbol");
            sessionService.setReturnState(chatId, ScalpingConfigState.NAME);
            return AiSelectSymbolState.NAME;
        }

        // быстрый выбор таймфрейма
        if (data.startsWith("scalp_tf:")) {
            String tf = data.substring("scalp_tf:".length());
            ScalpingStrategySettings s = settingsService.getOrCreate(chatId);
            s.setTimeframe(tf);
            settingsService.save(s);
            // остаёмся на этом же экране, чтобы увидеть "✅"
            sessionService.setEditingField(chatId, "timeframe");
            return NAME;
        }

        // числовые поля +/- шаг
        if (data.startsWith("scalp_inc:") || data.startsWith("scalp_dec:")) {
            String field  = data.substring(data.indexOf(':') + 1);
            String action = data.startsWith("scalp_inc:") ? "inc" : "dec";
            if (!META.containsKey(field)) return NAME;

            ScalpingStrategySettings s = settingsService.getOrCreate(chatId);
            adjustField(s, field, action);
            settingsService.save(s);
            return NAME;
        }

        if (ScalpingConfigState.NAME.equals(data)) {
            sessionService.clearEditingField(chatId);
            return ScalpingConfigState.NAME;
        }

        return NAME;
    }

    private String getValueAsString(ScalpingStrategySettings s, String field) {
        return switch (field) {
            case "symbol"               -> nvl(s.getSymbol());
            case "windowSize"           -> s.getWindowSize() == null ? "" : s.getWindowSize().toString();
            case "priceChangeThreshold" -> String.format("%.2f%%", s.getPriceChangeThreshold());
            case "spreadThreshold"      -> String.format("%.4f", s.getSpreadThreshold());
            case "takeProfitPct"        -> String.format("%.2f%%", s.getTakeProfitPct());
            case "stopLossPct"          -> String.format("%.2f%%", s.getStopLossPct());
            case "orderVolume"          -> String.format("%.4f", s.getOrderVolume());
            case "timeframe"            -> nvl(s.getTimeframe());
            case "cachedCandlesLimit"   -> s.getCachedCandlesLimit() == null ? "" : s.getCachedCandlesLimit().toString();
            default                     -> "";
        };
    }

    private void adjustField(ScalpingStrategySettings s, String field, String action) {
        FieldMeta meta = META.get(field);
        if (meta == null || meta.step() == null) return;

        double step = meta.step();
        double sign = "inc".equals(action) ? 1.0 : -1.0;

        switch (field) {
            case "windowSize" -> {
                int v = safeInt(s.getWindowSize()) + (int) Math.round(sign * step);
                s.setWindowSize(clampInt(v, 10_000));
            }
            case "priceChangeThreshold" -> {
                double v = s.getPriceChangeThreshold() + sign * step;
                s.setPriceChangeThreshold(clampDouble(v, 0.0, 10_000.0));
            }
            case "spreadThreshold" -> {
                double v = s.getSpreadThreshold() + sign * step;
                s.setSpreadThreshold(clampDouble(v, 0.0, 100.0));
            }
            case "takeProfitPct" -> {
                double v = s.getTakeProfitPct() + sign * step;
                s.setTakeProfitPct(clampDouble(v, 0.0, 10_000.0));
            }
            case "stopLossPct" -> {
                double v = s.getStopLossPct() + sign * step;
                s.setStopLossPct(clampDouble(v, 0.0, 10_000.0));
            }
            case "orderVolume" -> {
                double v = s.getOrderVolume() + sign * step;
                s.setOrderVolume(clampDouble(v, 0.0001, 1_000_000.0));
            }
            case "cachedCandlesLimit" -> {
                int v = safeInt(s.getCachedCandlesLimit()) + (int) Math.round(sign * step);
                s.setCachedCandlesLimit(clampInt(v, 1_000_000));
            }
            default -> { /* поля без шага — пропускаем */ }
        }
    }

    // ===== helpers =====

    private static int safeInt(Integer v) { return v == null ? 0 : v; }

    private static int clampInt(int v, int max) {
        return Math.max(1, Math.min(max, v));
    }

    private static double clampDouble(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String nvl(String s) { return (s == null || s.isBlank()) ? "—" : s; }

    private record FieldMeta(String label, String description, Double step) {}
}
