package com.chicu.aibot.bot.menu.feature.ai.strategy.scalping.service.impl;

import com.chicu.aibot.bot.menu.feature.ai.strategy.scalping.service.ScalpingPanelRenderer;
import com.chicu.aibot.bot.menu.feature.ai.strategy.view.LiveSnapshot;
import com.chicu.aibot.bot.ui.AdaptiveKeyboard;
import com.chicu.aibot.exchange.order.model.ExchangeOrderEntity;
import com.chicu.aibot.exchange.order.service.ExchangeOrderDbService;
import com.chicu.aibot.exchange.service.MarketLiveService;
import com.chicu.aibot.strategy.scalping.model.ScalpingStrategySettings;
import com.chicu.aibot.strategy.scalping.service.ScalpingStrategySettingsService;
import com.chicu.aibot.trading.trade.TradeLogService;
import com.chicu.aibot.trading.trade.model.TradeLogEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.List;
import java.util.Optional;

import static com.chicu.aibot.bot.menu.feature.ai.strategy.view.PanelTextUtils.*;

@Component
@RequiredArgsConstructor
public class ScalpingPanelRendererImpl implements ScalpingPanelRenderer {

    public static final String NAME               = "ai_trading_scalping_config";

    // Основные кнопки
    public static final String BTN_REFRESH        = "scalp_refresh";
    public static final String BTN_HELP           = "scalp_help";
    public static final String BTN_EDIT_SYMBOL    = "scalp_edit_symbol";
    public static final String BTN_TOGGLE_ACTIVE  = "scalp_toggle_active";

    // Кнопки редактирования параметров
    public static final String BTN_EDIT_TF        = "scalp_edit_timeframe";
    public static final String BTN_EDIT_ORDER_VOL = "scalp_edit_orderVolume";
    public static final String BTN_EDIT_HISTORY   = "scalp_edit_cachedCandlesLimit";
    public static final String BTN_EDIT_WINDOW    = "scalp_edit_windowSize";
    public static final String BTN_EDIT_DELTA_PCT = "scalp_edit_priceChangeThreshold";
    public static final String BTN_EDIT_SPREAD_PCT= "scalp_edit_spreadThreshold";
    public static final String BTN_EDIT_TP        = "scalp_edit_takeProfitPct";
    public static final String BTN_EDIT_SL        = "scalp_edit_stopLossPct";

    // Кнопки управления ордерами
    public static final String BTN_CANCEL_NEAREST = "scalp_cancel_nearest";
    public static final String BTN_CANCEL_ALL     = "scalp_cancel_all";

    private final ScalpingStrategySettingsService settingsService;
    private final MarketLiveService liveService;
    private final TradeLogService tradeLogService;
    private final ExchangeOrderDbService orderDb;

    @Override
    public SendMessage render(Long chatId) {
        ScalpingStrategySettings s = settingsService.getOrCreate(chatId);
        String symbol = nvl(s.getSymbol());
        LiveSnapshot live = liveService.build(chatId, symbol);

        // PnL из журнала сделок (как в Bollinger)
        Optional<TradeLogEntry> lastTradeOpt = tradeLogService.getLastTrade(chatId, symbol);
        String pnlBlock = lastTradeOpt.map(last -> buildPnlBlock(last, symbol, live)).orElse("_нет сделок_");
        String totalPnlBlock = formatTotalPnl(tradeLogService.getTotalPnl(chatId, symbol));

        // Открытые ордера
        List<ExchangeOrderEntity> openOrders = orderDb.findOpenByChatAndSymbol(chatId, symbol);
        String openOrdersBlock = formatOpenOrdersBlock(openOrders);

        String text = ("""
                *📊 Scalping Strategy*
                %s

                *Рынок:* `%s`
                %s Изм.: %s | 💵 Цена: `%s`

                *Баланс:*
                • %s: `%s`
                • %s: `%s`

                *Сделки / PnL:*
                %s
                %s

                *Открытые ордера (%d):*
                %s

                *Параметры:*
                • Объем: `%.4f`
                • Таймфрейм: `%s`
                • История: `%d`
                • Окно: `%d`
                • ΔЦены: `%.2f%%` • Спред: `%.2f%%`
                • TP: `%.2f%%` • SL: `%.2f%%`
                • Статус: *%s*
                """).stripTrailing().formatted(
                s.isActive() ? "🟢 *Запущена*" : "🔴 *Остановлена*",
                symbol,
                live.getChangePct() >= 0 ? "📈" : "📉",
                nvl(live.getChangeStr()),
                nvl(live.getPriceStr()),
                nvl(live.getBase()), nvl(live.getBaseBal()),
                nvl(live.getQuote()), nvl(live.getQuoteBal()),
                pnlBlock,
                totalPnlBlock,
                openOrders.size(),
                openOrdersBlock,
                safeD(s.getOrderVolume()),
                nvl(s.getTimeframe()),
                safeI(s.getCachedCandlesLimit()),
                safeI(s.getWindowSize()),
                safeD(s.getPriceChangeThreshold()),
                safeD(s.getSpreadThreshold()),
                safeD(s.getTakeProfitPct()),
                safeD(s.getStopLossPct()),
                s.isActive() ? "🟢 Запущена" : "🔴 Остановлена"
        );

        // Компактная раскладка (по 3 в ряд)
        InlineKeyboardMarkup markup = AdaptiveKeyboard.markupFromGroups(List.of(
                List.of(
                        AdaptiveKeyboard.btn("‹ Назад", "ai_trading"),
                        AdaptiveKeyboard.btn("⏱ Обновить", BTN_REFRESH),
                        AdaptiveKeyboard.btn("ℹ️ Описание", BTN_HELP)
                ),
                List.of(
                        AdaptiveKeyboard.btn("🎯 Символ", BTN_EDIT_SYMBOL),
                        AdaptiveKeyboard.btn("⏱ ТФ", BTN_EDIT_TF),
                        AdaptiveKeyboard.btn("📋 История", BTN_EDIT_HISTORY)
                ),
                List.of(
                        AdaptiveKeyboard.btn("💰 Объём %", BTN_EDIT_ORDER_VOL),
                        AdaptiveKeyboard.btn("🪟 Окно", BTN_EDIT_WINDOW),
                        AdaptiveKeyboard.btn("⚡ ΔЦены %", BTN_EDIT_DELTA_PCT)
                ),
                List.of(
                        AdaptiveKeyboard.btn("↔️ Спред %", BTN_EDIT_SPREAD_PCT),
                        AdaptiveKeyboard.btn("🎯 TP %", BTN_EDIT_TP),
                        AdaptiveKeyboard.btn("🛡 SL %", BTN_EDIT_SL)
                ),
                List.of(
                        AdaptiveKeyboard.btn("❌ Ближайший ордер", BTN_CANCEL_NEAREST),
                        AdaptiveKeyboard.btn("🧹 Отменить все", BTN_CANCEL_ALL),
                        AdaptiveKeyboard.btn(s.isActive() ? "🔴 Остановить" : "🟢 Запустить", BTN_TOGGLE_ACTIVE)
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

    /* ===== локальные безопасные хелперы, как в других панелях ===== */
    private static int safeI(Integer v) { return v == null ? 0 : v; }
    private static double safeD(Double v) { return v == null ? 0d : v; }
}
