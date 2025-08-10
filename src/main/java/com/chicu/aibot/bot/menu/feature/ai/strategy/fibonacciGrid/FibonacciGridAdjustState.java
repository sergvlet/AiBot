package com.chicu.aibot.bot.menu.feature.ai.strategy.fibonacciGrid;

import com.chicu.aibot.bot.menu.core.MenuSessionService;
import com.chicu.aibot.bot.menu.core.MenuState;
import com.chicu.aibot.bot.menu.feature.ai.strategy.AiSelectStrategyState;
import com.chicu.aibot.strategy.fibonacci.model.FibonacciGridStrategySettings;
import com.chicu.aibot.strategy.fibonacci.service.FibonacciGridStrategySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class FibonacciGridAdjustState implements MenuState {
    public static final String NAME = "ai_trading_fibonacci_adjust";

    private final FibonacciGridStrategySettingsService service;
    private final MenuSessionService sessionService;
    private final AiSelectStrategyState selectStrategyState;

    /** Метаданные: label, описание и шаг */
    private static final Map<String, FieldMeta> META = Map.ofEntries(
            Map.entry("symbol",             new FieldMeta("Символ",     "Торговый символ, например BTCUSDT", null)),
            Map.entry("levels",             new FieldMeta("Уровни",      "Список уровней Фибоначчи",          null)),
            Map.entry("gridSizePct",        new FieldMeta("Шаг сетки",   "Расстояние между ордерами в %",     0.1)),
            Map.entry("orderVolume",        new FieldMeta("Объём",       "Количество актива в ордере",        1.0)),
            Map.entry("maxActiveOrders",    new FieldMeta("Макс. ордеров", "Максимум открытых ордеров",       1.0)),
            Map.entry("takeProfitPct",      new FieldMeta("Take-Profit", "Фиксация профита, % от цены",       0.5)),
            Map.entry("stopLossPct",        new FieldMeta("Stop-Loss",   "Лимит убытка, % от цены",           0.5)),
            Map.entry("allowShort",         new FieldMeta("Шорт?",       "Можно открывать короткие позиции?",  null)),
            Map.entry("allowLong",          new FieldMeta("Лонг?",       "Можно открывать длинные позиции?",   null)),
            Map.entry("timeframe",          new FieldMeta("Таймфрейм",   "Интервал свечей, напр. 1h",         null)),
            Map.entry("cachedCandlesLimit", new FieldMeta("История",     "Кол-во свечей для анализа",         10.0))
    );

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public SendMessage render(Long chatId) {
        String field = sessionService.getEditingField(chatId);
        // 1) Если непонятно, что редактировать — возвращаем список стратегий
        if (field == null || !META.containsKey(field)) {
            sessionService.clearEditingField(chatId);
            return selectStrategyState.render(chatId);
        }

        FibonacciGridStrategySettings s = service.getOrCreate(chatId);
        FieldMeta meta = META.get(field);
        String current = getValueAsString(s, field);

        // «-» / «+»
        List<InlineKeyboardButton> adjustButtons = List.of(
                InlineKeyboardButton.builder().text("➖").callbackData("fibo_dec:" + field).build(),
                InlineKeyboardButton.builder().text("➕").callbackData("fibo_inc:" + field).build()
        );
        // Кнопка «Назад»
        InlineKeyboardButton back = InlineKeyboardButton.builder()
                .text("‹ Назад")
                .callbackData("ai_trading_fibonacci_config")
                .build();

        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboard(List.of(
                        adjustButtons,
                        List.of(back)
                ))
                .build();

        String text = String.format(
                "*%s*\n\n%s\n\nТекущее значение: `%s`",
                meta.label, meta.description, current
        );

        return SendMessage.builder()
                .chatId(chatId.toString())
                .parseMode("Markdown")
                .text(text)
                .replyMarkup(markup)
                .build();
    }

    @Override
    public String handleInput(Update update) {
        var cq = update.getCallbackQuery();
        String data   = cq.getData();
        Long   chatId = cq.getMessage().getChatId();

        // Инкремент / декремент
        if (data.startsWith("fibo_inc:") || data.startsWith("fibo_dec:")) {
            String[] parts = data.split(":", 2);
            String action = parts[0].endsWith("inc") ? "inc" : "dec";
            String field  = parts[1];

            FibonacciGridStrategySettings s = service.getOrCreate(chatId);
            adjustField(s, field, action);
            service.save(s);
            return NAME;
        }

        // Назад в меню конфигурации
        if ("ai_trading_fibonacci_config".equals(data)) {
            sessionService.clearEditingField(chatId);
            return FibonacciGridConfigState.NAME;
        }

        return NAME;
    }


    private String getValueAsString(FibonacciGridStrategySettings s, String field) {
        return switch (field) {
            case "symbol"             -> s.getSymbol();
            case "gridSizePct"        -> String.format("%.2f%%", s.getGridSizePct());
            case "orderVolume"        -> String.format("%.4f", s.getOrderVolume());
            case "maxActiveOrders"    -> s.getMaxActiveOrders().toString();
            case "takeProfitPct"      -> String.format("%.2f%%", s.getTakeProfitPct());
            case "stopLossPct"        -> String.format("%.2f%%", s.getStopLossPct());
            case "allowShort"         -> s.getAllowShort().toString();
            case "allowLong"          -> s.getAllowLong().toString();
            case "timeframe"          -> s.getTimeframe();
            case "cachedCandlesLimit" -> s.getCachedCandlesLimit().toString();
            default                   -> "";
        };
    }

    private void adjustField(FibonacciGridStrategySettings s, String field, String action) {
        FieldMeta meta = META.get(field);
        if (meta.step == null) {
            if ("allowShort".equals(field)) s.setAllowShort(!s.getAllowShort());
            if ("allowLong".equals(field))  s.setAllowLong(!s.getAllowLong());
            return;
        }
        double step = meta.step;
        switch (field) {
            case "gridSizePct" ->
                    s.setGridSizePct(action.equals("inc") ? s.getGridSizePct() + step : s.getGridSizePct() - step);
            case "orderVolume" ->
                    s.setOrderVolume(action.equals("inc") ? s.getOrderVolume() + step : s.getOrderVolume() - step);
            case "maxActiveOrders" -> {
                int delta = meta.step.intValue();
                s.setMaxActiveOrders(action.equals("inc") ?
                        s.getMaxActiveOrders() + delta : s.getMaxActiveOrders() - delta);
            }
            case "takeProfitPct" ->
                    s.setTakeProfitPct(action.equals("inc") ? s.getTakeProfitPct() + step : s.getTakeProfitPct() - step);
            case "stopLossPct" ->
                    s.setStopLossPct(action.equals("inc") ? s.getStopLossPct() + step : s.getStopLossPct() - step);
            case "cachedCandlesLimit" -> {
                int delta = meta.step.intValue();
                s.setCachedCandlesLimit(action.equals("inc") ?
                        s.getCachedCandlesLimit() + delta : s.getCachedCandlesLimit() - delta);
            }
        }
    }

    private record FieldMeta(String label, String description, Double step) {}
}
