package com.chicu.aibot.bot.menu.feature.ai.strategy.scalping;

import com.chicu.aibot.bot.menu.core.MenuSessionService;
import com.chicu.aibot.bot.menu.core.MenuState;
import com.chicu.aibot.bot.menu.feature.ai.strategy.AiSelectStrategyState;
import com.chicu.aibot.strategy.scalping.model.ScalpingStrategySettings;
import com.chicu.aibot.strategy.scalping.service.ScalpingStrategySettingsService;
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
public class ScalpingAdjustState implements MenuState {
    public static final String NAME = "ai_trading_scalping_adjust";

    private final ScalpingStrategySettingsService settingsService;
    private final MenuSessionService sessionService;
    private final AiSelectStrategyState selectStrategyState;

    private static final Map<String, FieldMeta> META = Map.ofEntries(
            Map.entry("symbol",               new FieldMeta("Символ", "Торговая пара, например BTCUSDT", null)),
            Map.entry("windowSize",           new FieldMeta("Окно", "Размер окна (кол-во свечей)", 1.0)),
            Map.entry("priceChangeThreshold", new FieldMeta("Порог движения", "Изменение цены в % для входа", 0.1)),
            Map.entry("spreadThreshold",      new FieldMeta("Спред", "Макс. допустимый спред", 0.01)),
            Map.entry("takeProfitPct",        new FieldMeta("TP", "Take-Profit в %", 0.5)),
            Map.entry("stopLossPct",          new FieldMeta("SL", "Stop-Loss в %", 0.5)),
            Map.entry("orderVolume",          new FieldMeta("Объем", "Объем рыночного ордера", 0.01)),
            Map.entry("timeframe",            new FieldMeta("Таймфрейм", "Таймфрейм свечей (например 1m)", null)),
            Map.entry("cachedCandlesLimit",   new FieldMeta("История", "Кол-во свечей для анализа", 10.0))
    );

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public SendMessage render(Long chatId) {
        String field = sessionService.getEditingField(chatId);
        if (field == null || !META.containsKey(field)) {
            sessionService.clearEditingField(chatId);
            return selectStrategyState.render(chatId);
        }

        ScalpingStrategySettings s = settingsService.getOrCreate(chatId);
        FieldMeta meta = META.get(field);
        String current = getValueAsString(s, field);

        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboard(List.of(
                        List.of(
                                InlineKeyboardButton.builder().text("➖").callbackData("scalp_dec:" + field).build(),
                                InlineKeyboardButton.builder().text("➕").callbackData("scalp_inc:" + field).build()
                        ),
                        List.of(InlineKeyboardButton.builder()
                                .text("‹ Назад")
                                .callbackData("ai_trading_scalping_config")
                                .build())
                ))
                .build();

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

    @Override
    public String handleInput(Update update) {
        String data = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();

        if (data.startsWith("scalp_inc:") || data.startsWith("scalp_dec:")) {
            String[] parts = data.split(":", 2);
            String field = parts[1];
            String action = data.startsWith("scalp_inc:") ? "inc" : "dec";

            ScalpingStrategySettings s = settingsService.getOrCreate(chatId);
            adjustField(s, field, action);
            settingsService.save(s);
            return NAME;
        }

        if ("ai_trading_scalping_config".equals(data)) {
            sessionService.clearEditingField(chatId);
            return ScalpingConfigState.NAME;
        }

        return NAME;
    }

    private String getValueAsString(ScalpingStrategySettings s, String field) {
        return switch (field) {
            case "symbol"               -> s.getSymbol();
            case "windowSize"           -> s.getWindowSize().toString();
            case "priceChangeThreshold" -> String.format("%.2f%%", s.getPriceChangeThreshold());
            case "spreadThreshold"      -> String.format("%.4f", s.getSpreadThreshold());
            case "takeProfitPct"        -> String.format("%.2f%%", s.getTakeProfitPct());
            case "stopLossPct"          -> String.format("%.2f%%", s.getStopLossPct());
            case "orderVolume"          -> String.format("%.4f", s.getOrderVolume());
            case "timeframe"            -> s.getTimeframe();
            case "cachedCandlesLimit"   -> s.getCachedCandlesLimit().toString();
            default                     -> "";
        };
    }

    private void adjustField(ScalpingStrategySettings s, String field, String action) {
        FieldMeta meta = META.get(field);
        if (meta.step() == null) return;

        double step = meta.step();
        switch (field) {
            case "windowSize" -> s.setWindowSize((int) (s.getWindowSize() + (action.equals("inc") ? step : -step)));
            case "priceChangeThreshold" -> s.setPriceChangeThreshold(s.getPriceChangeThreshold() + (action.equals("inc") ? step : -step));
            case "spreadThreshold" -> s.setSpreadThreshold(s.getSpreadThreshold() + (action.equals("inc") ? step : -step));
            case "takeProfitPct" -> s.setTakeProfitPct(s.getTakeProfitPct() + (action.equals("inc") ? step : -step));
            case "stopLossPct" -> s.setStopLossPct(s.getStopLossPct() + (action.equals("inc") ? step : -step));
            case "orderVolume" -> s.setOrderVolume(s.getOrderVolume() + (action.equals("inc") ? step : -step));
            case "cachedCandlesLimit" -> s.setCachedCandlesLimit((int) (s.getCachedCandlesLimit() + (action.equals("inc") ? step : -step)));
        }
    }

    private record FieldMeta(String label, String description, Double step) {}
}
