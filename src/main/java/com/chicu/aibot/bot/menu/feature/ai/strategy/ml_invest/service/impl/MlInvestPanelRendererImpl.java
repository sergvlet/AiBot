package com.chicu.aibot.bot.menu.feature.ai.strategy.ml_invest.service.impl;

import com.chicu.aibot.bot.menu.feature.ai.strategy.ml_invest.service.MlInvestPanelRenderer;
import com.chicu.aibot.bot.ui.AdaptiveKeyboard;
import com.chicu.aibot.bot.util.TelegramText;
import com.chicu.aibot.exchange.order.service.ExchangeOrderDbService;
import com.chicu.aibot.exchange.service.MarketLiveService;
import com.chicu.aibot.strategy.ml_invest.model.MachineLearningInvestStrategySettings;
import com.chicu.aibot.strategy.ml_invest.service.MachineLearningInvestStrategySettingsService;
import com.chicu.aibot.trading.trade.TradeLogService;
import com.chicu.aibot.trading.trade.model.TradeLogEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static com.chicu.aibot.bot.menu.feature.ai.strategy.view.PanelTextUtils.*;

/**
 * Telegram-панель стратегии Machine Learning Invest.
 * Безопасный MarkdownV2 + экранирование спецсимволов.
 */
@Component
@RequiredArgsConstructor
public class MlInvestPanelRendererImpl implements MlInvestPanelRenderer {

    public static final String NAME                = "ai_trading_ml_invest_config";
    public static final String BTN_REFRESH         = "ai_trading_ml_invest_refresh";
    public static final String BTN_TOGGLE_ACTIVE   = "ai_trading_ml_invest_toggle";
    public static final String BTN_EDIT_SYMBOL     = "ml_edit_symbol";
    public static final String BTN_EDIT_TF         = "ml_edit_timeframe";
    public static final String BTN_EDIT_CANDLES    = "ml_edit_cachedCandlesLimit";
    public static final String BTN_EDIT_THRESHOLDS = "ml_edit_thresholds";
    public static final String BTN_EDIT_TP_SL      = "ml_edit_tp_sl";
    public static final String BTN_EDIT_MODEL      = "ml_edit_modelPath";
    public static final String BTN_EDIT_VOLUME     = "ml_edit_volumeMode";
    public static final String BTN_EDIT_QTY        = "ml_edit_orderQty";
    public static final String BTN_EDIT_QUOTE      = "ml_edit_orderQuoteAmount";
    public static final String BTN_HELP            = "ai_trading_ml_invest_help";

    private final MachineLearningInvestStrategySettingsService settingsService;
    private final TradeLogService tradeLogService;
    private final ExchangeOrderDbService orderDb;
    private final MarketLiveService liveService;

    @Override
    public SendMessage render(Long chatId) {
        MachineLearningInvestStrategySettings s = settingsService.getOrCreate(chatId);
        String symbol = nvl(s.getSymbol());
        var live = liveService.build(chatId, symbol);

        Optional<TradeLogEntry> lastTradeOpt = tradeLogService.getLastTrade(chatId, symbol);
        String pnlBlock = lastTradeOpt.map(last -> buildPnlBlock(last, symbol, live)).orElse("_нет сделок_");
        String totalPnlBlock = formatTotalPnl(tradeLogService.getTotalPnl(chatId, symbol));
        var openOrders = orderDb.findOpenByChatAndSymbol(chatId, symbol);
        String openOrdersBlock = formatOpenOrdersBlock(openOrders);

        String volumeModeLine = s.isUseQuoteAmount()
                ? "• Режим объёма: по *сумме (Quote)*, " + fmt(s.getOrderQuoteAmount())
                : "• Режим объёма: по *количеству (Qty)*, " + fmt(s.getOrderQty());

        String text = ("""
                *🤖 Machine Learning Invest*
                Статус: %s

                *Рынок:* `%s`
                %s Изм.: %s | 💵 Цена: `%s`

                *Сделки / PnL:*
                %s
                %s

                *Открытые ордера (%d):*
                %s

                *Параметры:*
                • Таймфрейм: `%s`
                • Свечи: `%d`
                • BUY threshold: `%s`
                • SELL threshold: `%s`
                • TP: `%s%%` • SL: `%s%%`
                • Модель: `%s`
                %s
                """).stripTrailing().formatted(
                s.isActive() ? "🟢 *Запущена*" : "🔴 *Остановлена*",
                symbol,
                (live.getChangePct() >= 0 ? "📈" : "📉"),
                nvl(live.getChangeStr()),
                nvl(live.getPriceStr()),
                pnlBlock,
                totalPnlBlock,
                openOrders.size(),
                openOrdersBlock,
                nvl(s.getTimeframe()),
                safeI(s.getCachedCandlesLimit()),
                fmt(s.getBuyThreshold()),
                fmt(s.getSellThreshold()),
                fmt(s.getTakeProfitPct()),
                fmt(s.getStopLossPct()),
                nvl(s.getModelPath()),
                volumeModeLine
        );

        // ✅ экранируем Markdown-символы, включая точки
        text = TelegramText.escapeMarkdownV1(text);

        InlineKeyboardMarkup markup = AdaptiveKeyboard.markupFromGroups(List.of(
                List.of(
                        AdaptiveKeyboard.btn("‹ Назад", "ai_trading"),
                        AdaptiveKeyboard.btn("⏱ Обновить", BTN_REFRESH),
                        AdaptiveKeyboard.btn("ℹ️ Описание", BTN_HELP)
                ),
                List.of(
                        AdaptiveKeyboard.btn("💱 Символ", BTN_EDIT_SYMBOL),
                        AdaptiveKeyboard.btn("🕒 Таймфрейм", BTN_EDIT_TF),
                        AdaptiveKeyboard.btn("📊 Свечи", BTN_EDIT_CANDLES)
                ),
                List.of(
                        AdaptiveKeyboard.btn("🎯 BUY/SELL thr", BTN_EDIT_THRESHOLDS),
                        AdaptiveKeyboard.btn("💰 TP / SL", BTN_EDIT_TP_SL)
                ),
                List.of(
                        AdaptiveKeyboard.btn("🤖 Модель", BTN_EDIT_MODEL),
                        AdaptiveKeyboard.btn("⚙️ Объём", BTN_EDIT_VOLUME)
                ),
                List.of(
                        AdaptiveKeyboard.btn("Qty", BTN_EDIT_QTY),
                        AdaptiveKeyboard.btn("Quote", BTN_EDIT_QUOTE)
                ),
                List.of(
                        AdaptiveKeyboard.btn(s.isActive() ? "🔴 Остановить стратегию" : "🟢 Запустить стратегию", BTN_TOGGLE_ACTIVE)
                )
        ), 3);
        return SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode("Markdown")
                .disableWebPagePreview(true)
                .replyMarkup(markup)
                .build();
    }

    private static int safeI(Integer v) { return v == null ? 0 : v; }

    private static String fmt(BigDecimal v) {
        if (v == null) return "—";
        return v.stripTrailingZeros().toPlainString();
    }
}
