package com.chicu.aibot.bot.menu.feature.ai.strategy.ml_invest;

import com.chicu.aibot.bot.menu.core.MenuSessionService;
import com.chicu.aibot.bot.menu.core.MenuState;
import com.chicu.aibot.strategy.ml_invest.model.MachineLearningInvestStrategySettings;
import com.chicu.aibot.strategy.ml_invest.service.MachineLearningInvestStrategySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.math.BigDecimal;
import java.util.*;

@Component(AiTradingMlInvestAdjustState.NAME)
@RequiredArgsConstructor
public class AiTradingMlInvestAdjustState implements MenuState {

    public static final String NAME = "ai_trading_ml_invest_adjust";

    private final MenuSessionService sessionService;
    private final MachineLearningInvestStrategySettingsService settingsService;

    @Override
    public String name() { return NAME; }

    @Override
    public SendMessage render(Long chatId) {
        if (chatId == null) {
            return SendMessage.builder().chatId("0").text("Ошибка: chatId не определён.").build();
        }

        MachineLearningInvestStrategySettings s = settingsService.getOrCreate(chatId);
        String field = sessionService.getEditingField(chatId);
        FieldMeta meta = fields().getOrDefault(field, new FieldMeta("Параметр", "—", null));

        String valueStr = switch (field) {
            case "timeframe"          -> nullSafe(s.getTimeframe());
            case "cachedCandlesLimit" -> String.valueOf(safeInt(s.getCachedCandlesLimit()));
            case "buyThreshold"       -> nullSafe(s.getBuyThreshold());
            case "sellThreshold"      -> nullSafe(s.getSellThreshold());
            case "tp_sl"              -> "TP=" + nullSafe(s.getTakeProfitPct()) + " / SL=" + nullSafe(s.getStopLossPct());
            case "modelPath"          -> nullSafe(s.getModelPath());
            case "orderQty"           -> nullSafe(s.getOrderQty());
            case "orderQuoteAmount"   -> nullSafe(s.getOrderQuoteAmount());
            case "volumeMode"         -> s.isUseQuoteAmount() ? "по сумме (Quote)" : "по количеству (Qty)";
            default                   -> "—";
        };

        String text = """
                🔧 *%s*
                _%s_

                Текущее значение: *%s*
                """.formatted(meta.label(), meta.description(), valueStr);

        InlineKeyboardMarkup kb = buildKb(field, s.isUseQuoteAmount());
        return SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(text)
                .parseMode("Markdown")
                .replyMarkup(kb)
                .build();
    }

    // Удобная обёртка
    public SendMessage render(Update update) { return render(extractChatId(update)); }

    @Override
    public String handleInput(Update update) {
        if (update == null || !update.hasCallbackQuery()) return NAME;

        long chatId = update.getCallbackQuery().getMessage().getChatId();
        String data = update.getCallbackQuery().getData();

        MachineLearningInvestStrategySettings s = settingsService.getOrCreate(chatId);
        String field = sessionService.getEditingField(chatId);

        if ("back".equals(data)) return AiTradingMlInvestConfigState.NAME;

        if ("volumeMode".equals(field)) {
            if ("mode:qty".equals(data))   s.setUseQuoteAmount(false);
            if ("mode:quote".equals(data)) s.setUseQuoteAmount(true);
            settingsService.save(s);
            return NAME;
        }

        if ("timeframe".equals(field)) {
            if (data != null && data.startsWith("tf:")) {
                s.setTimeframe(data.substring(3));
                settingsService.save(s);
            }
            return NAME;
        }

        if ("modelPath".equals(field)) {
            if ("model:ask".equals(data)) return "ai_trading_ml_invest_help";
            return NAME;
        }

        switch (field) {
            case "cachedCandlesLimit" -> {
                int v = safeInt(s.getCachedCandlesLimit());
                if ("dec".equals(data)) v = clampInt(v - 50);
                if ("inc".equals(data)) v = clampInt(v + 50);
                s.setCachedCandlesLimit(v);
            }
            case "buyThreshold" -> {
                double v = s.getBuyThreshold() == null ? 0.60 : s.getBuyThreshold().doubleValue();
                if ("dec".equals(data)) v -= 0.05;
                if ("inc".equals(data)) v += 0.05;
                s.setBuyThreshold(clampDoubleToBigDecimal(v, 0.05, 0.99));
            }
            case "sellThreshold" -> {
                double v = s.getSellThreshold() == null ? 0.60 : s.getSellThreshold().doubleValue();
                if ("dec".equals(data)) v -= 0.05;
                if ("inc".equals(data)) v += 0.05;
                s.setSellThreshold(clampDoubleToBigDecimal(v, 0.05, 0.99));
            }
            case "tp_sl" -> {
                double tp = s.getTakeProfitPct() == null ? 0.0 : s.getTakeProfitPct().doubleValue();
                double sl = s.getStopLossPct()   == null ? 0.0 : s.getStopLossPct().doubleValue();
                if ("tp_dec".equals(data)) tp -= 0.1;
                if ("tp_inc".equals(data)) tp += 0.1;
                if ("sl_dec".equals(data)) sl -= 0.1;
                if ("sl_inc".equals(data)) sl += 0.1;
                s.setTakeProfitPct(clampDoubleToBigDecimal(tp, 0.0, 10_000.0));
                s.setStopLossPct(clampDoubleToBigDecimal(sl, 0.0, 10_000.0));
            }
            case "orderQty" -> {
                BigDecimal v = s.getOrderQty() == null ? BigDecimal.ZERO : s.getOrderQty();
                v = stepDecimal(v, data, new BigDecimal("0.001"), new BigDecimal("0.01"), new BigDecimal("0.1"));
                if (v.signum() < 0) v = BigDecimal.ZERO;
                s.setOrderQty(v);
                // при желании можно автопереключать режим:
                // s.setUseQuoteAmount(false);
            }
            case "orderQuoteAmount" -> {
                BigDecimal v = s.getOrderQuoteAmount() == null ? BigDecimal.ZERO : s.getOrderQuoteAmount();
                v = stepDecimal(v, data, new BigDecimal("1"), new BigDecimal("10"), new BigDecimal("100"));
                if (v.signum() < 0) v = BigDecimal.ZERO;
                s.setOrderQuoteAmount(v);
                // s.setUseQuoteAmount(true);
            }
            default -> { /* noop */ }
        }

        settingsService.save(s);
        return NAME;
    }

    /* ===== helpers ===== */

    private InlineKeyboardMarkup buildKb(String field, boolean useQuoteAmount) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        switch (field) {
            case "volumeMode" -> {
                String left  = useQuoteAmount ? "▫️ Qty (по количеству)" : "🔘 Qty (по количеству)";
                String right = useQuoteAmount ? "🔘 Quote (по сумме)"   : "▫️ Quote (по сумме)";
                rows.add(row(btn(left, "mode:qty"), btn(right, "mode:quote")));
            }
            case "timeframe" -> {
                rows.add(row(btn("1m", "tf:1m"), btn("5m", "tf:5m"), btn("15m", "tf:15m")));
                rows.add(row(btn("1h", "tf:1h"), btn("4h", "tf:4h"), btn("1d", "tf:1d")));
            }
            case "cachedCandlesLimit" -> rows.add(row(btn("➖ 50", "dec"), btn("➕ 50", "inc")));
            case "buyThreshold", "sellThreshold" -> rows.add(row(btn("➖ 0.05", "dec"), btn("➕ 0.05", "inc")));
            case "tp_sl" -> {
                rows.add(row(btn("TP ➖", "tp_dec"), btn("TP ➕", "tp_inc")));
                rows.add(row(btn("SL ➖", "sl_dec"), btn("SL ➖", "sl_inc")));
            }
            case "modelPath" -> rows.add(row(btn("✏️ Ввести путь к модели", "model:ask")));
            case "orderQty" -> {
                rows.add(row(btn("➖ 0.001", "dec_small"), btn("➖ 0.01", "dec_mid"), btn("➖ 0.1", "dec_big")));
                rows.add(row(btn("➕ 0.001", "inc_small"), btn("➕ 0.01", "inc_mid"), btn("➕ 0.1", "inc_big")));
            }
            case "orderQuoteAmount" -> {
                rows.add(row(btn("➖ 1", "dec_small"), btn("➖ 10", "dec_mid"), btn("➖ 100", "dec_big")));
                rows.add(row(btn("➕ 1", "inc_small"), btn("➕ 10", "inc_mid"), btn("➕ 100", "inc_big")));
            }
            default -> { /* nothing */ }
        }

        rows.add(row(btn("‹ Назад", "back")));

        InlineKeyboardMarkup kb = new InlineKeyboardMarkup();
        kb.setKeyboard(rows);
        return kb;
    }

    private Map<String, FieldMeta> fields() {
        Map<String, FieldMeta> m = new HashMap<>();
        m.put("volumeMode",         new FieldMeta("Режим объёма", "Выберите способ расчёта объёма: Qty или Quote", null));
        m.put("timeframe",          new FieldMeta("Таймфрейм", "Выберите интервал свечей", null));
        m.put("cachedCandlesLimit", new FieldMeta("Свечи", "Количество последних свечей для анализа", null));
        m.put("buyThreshold",       new FieldMeta("Порог BUY", "Порог вероятности покупки (0.05..0.99), шаг 0.05", 0.05));
        m.put("sellThreshold",      new FieldMeta("Порог SELL", "Порог вероятности продажи (0.05..0.99), шаг 0.05", 0.05));
        m.put("tp_sl",              new FieldMeta("TP / SL", "Тейк-профит и стоп-лосс, шаг 0.1", 0.1));
        m.put("modelPath",          new FieldMeta("Модель", "Путь к ML-модели (.joblib/.onnx)", null));
        m.put("orderQty",           new FieldMeta("Фикс. объём (Qty)", "Количество базового актива на сделку", 0.001));
        m.put("orderQuoteAmount",   new FieldMeta("Сумма (Quote)", "Сумма в котируемой валюте; qty считается по цене", 1.0));
        return m;
    }

    private InlineKeyboardButton btn(String t, String d) { var b = new InlineKeyboardButton(t); b.setCallbackData(d); return b; }
    private List<InlineKeyboardButton> row(InlineKeyboardButton... a) { return new ArrayList<>(List.of(a)); }

    private static int safeInt(Integer v) { return v == null ? 0 : v; }
    private static int clampInt(int v) { return Math.max(1, Math.min(10_000, v)); }
    private static BigDecimal clampDoubleToBigDecimal(double v, double min, double max) {
        return BigDecimal.valueOf(Math.max(min, Math.min(max, v)));
    }
    private static BigDecimal stepDecimal(BigDecimal base, String data, BigDecimal small, BigDecimal mid, BigDecimal big) {
        return switch (data) {
            case "dec_small" -> base.subtract(small);
            case "dec_mid"   -> base.subtract(mid);
            case "dec_big"   -> base.subtract(big);
            case "inc_small" -> base.add(small);
            case "inc_mid"   -> base.add(mid);
            case "inc_big"   -> base.add(big);
            default -> base;
        };
    }

    private String nullSafe(Object o) { return o == null ? "—" : String.valueOf(o); }

    private long extractChatId(Update u) {
        if (u != null) {
            if (u.hasCallbackQuery() && u.getCallbackQuery().getMessage() != null) {
                return u.getCallbackQuery().getMessage().getChatId();
            }
            if (u.hasMessage() && u.getMessage().getChatId() != null) {
                return u.getMessage().getChatId();
            }
        }
        return 0L;
    }

    private record FieldMeta(String label, String description, Double step) {}
}
