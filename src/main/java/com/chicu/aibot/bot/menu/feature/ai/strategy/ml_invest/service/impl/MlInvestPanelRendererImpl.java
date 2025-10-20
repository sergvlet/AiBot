package com.chicu.aibot.bot.menu.feature.ai.strategy.ml_invest.service.impl;

import com.chicu.aibot.bot.menu.feature.ai.strategy.ml_invest.AiTradingMlInvestAdjustState;
import com.chicu.aibot.bot.menu.feature.ai.strategy.ml_invest.service.MlInvestPanelRenderer;
import com.chicu.aibot.strategy.ml_invest.model.MachineLearningInvestStrategySettings;
import com.chicu.aibot.strategy.ml_invest.service.MachineLearningInvestStrategySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MlInvestPanelRendererImpl implements MlInvestPanelRenderer {

    public static final String BTN_REFRESH       = "ai_trading_ml_invest_refresh";
    public static final String BTN_TOGGLE_ACTIVE = "ai_trading_ml_invest_toggle";
    public static final String BTN_EDIT_SYMBOL   = "ml_edit_symbol";
    public static final String BTN_HELP          = "ai_trading_ml_invest_help";

    private final MachineLearningInvestStrategySettingsService settingsService;

    @Override
    public SendMessage render(Long chatId) {
        MachineLearningInvestStrategySettings s = settingsService.getOrCreate(chatId);

        String status = s.isActive() ? "✅ активна" : "❌ остановлена";
        String volumeModeLine = s.isUseQuoteAmount()
                ? "• Режим объёма: по сумме (Quote)\n• Quote: " + fmt(s.getOrderQuoteAmount())
                : "• Режим объёма: по количеству (Qty)\n• Qty: " + fmt(s.getOrderQty());

        String volumeWarning = "";
        if (!s.isUseQuoteAmount() && (s.getOrderQty() == null || s.getOrderQty().signum() <= 0)) {
            volumeWarning = "\n⚠️ Qty не задан — сделки не будут выставляться";
        }
        if (s.isUseQuoteAmount() && (s.getOrderQuoteAmount() == null || s.getOrderQuoteAmount().signum() <= 0)) {
            volumeWarning = "\n⚠️ Quote не задан — сделки не будут выставляться";
        }

        String text = """
                🤖 *Machine Learning Invest*
                Статус: %s

                Рынок: %s
                Δ Изм.: — | 💵 Цена: —

                Параметры стратегии:
                • TF: %s   • Свечи: %s
                • BUY thr: %s   • SELL thr: %s
                • TP%%: %s   • SL%%: %s
                • Модель: %s
                %s%s
                """.formatted(
                status,
                nullSafe(s.getSymbol()),
                nullSafe(s.getTimeframe()),
                s.getCachedCandlesLimit() == null ? "—" : String.valueOf(s.getCachedCandlesLimit()),
                nullSafe(s.getBuyThreshold()),
                nullSafe(s.getSellThreshold()),
                nullSafe(s.getTakeProfitPct()),
                nullSafe(s.getStopLossPct()),
                nullSafe(s.getModelPath()),
                "\n" + volumeModeLine,
                volumeWarning
        );

        InlineKeyboardMarkup kb = buildKeyboard(s);
        return SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(text)
                .parseMode("Markdown")
                .replyMarkup(kb)
                .build();
    }

    private InlineKeyboardMarkup buildKeyboard(MachineLearningInvestStrategySettings s) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(List.of(
                btn(s.isActive() ? "⏸ Остановить" : "▶️ Запустить", BTN_TOGGLE_ACTIVE),
                btn("🔄 Обновить", BTN_REFRESH)
        ));
        rows.add(List.of(
                btn("« Назад", "ai_trading"),
                btn("ℹ️ Помощь", BTN_HELP)
        ));
        // Редактирование ключевых полей
        rows.add(List.of(
                btn("Таймфрейм", "ml_edit_timeframe"),
                btn("Свечи", "ml_edit_cachedCandlesLimit")
        ));
        rows.add(List.of(
                btn("BUY thr", "ml_edit_buyThreshold"),
                btn("SELL thr", "ml_edit_sellThreshold")
        ));
        rows.add(List.of(
                btn("TP / SL", "ml_edit_tp_sl"),
                btn("Модель", "ml_edit_modelPath")
        ));
        rows.add(List.of(
                btn("Символ", BTN_EDIT_SYMBOL),
                btn("Режим объёма", "ml_edit_volumeMode")
        ));
        rows.add(List.of(
                btn("Qty", "ml_edit_orderQty"),
                btn("Quote", "ml_edit_orderQuoteAmount")
        ));

        InlineKeyboardMarkup kb = new InlineKeyboardMarkup();
        kb.setKeyboard(rows);
        return kb;
    }

    private InlineKeyboardButton btn(String t, String d) {
        InlineKeyboardButton b = new InlineKeyboardButton(t);
        b.setCallbackData(d);
        return b;
    }

    private String fmt(BigDecimal v) { return v == null ? "—" : v.toPlainString(); }
    private String nullSafe(Object o) { return o == null ? "—" : String.valueOf(o); }
}
