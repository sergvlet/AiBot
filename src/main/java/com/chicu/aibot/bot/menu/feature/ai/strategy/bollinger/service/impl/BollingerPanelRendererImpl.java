package com.chicu.aibot.bot.menu.feature.ai.strategy.bollinger.service.impl;

import com.chicu.aibot.bot.menu.feature.ai.strategy.bollinger.service.BollingerPanelRenderer;
import com.chicu.aibot.bot.menu.feature.ai.strategy.view.LiveSnapshot;
import com.chicu.aibot.bot.ui.AdaptiveKeyboard;
import com.chicu.aibot.exchange.order.service.ExchangeOrderDbService;
import com.chicu.aibot.exchange.service.MarketLiveService;
import com.chicu.aibot.strategy.bollinger.model.BollingerStrategySettings;
import com.chicu.aibot.strategy.bollinger.service.BollingerStrategySettingsService;
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
public class BollingerPanelRendererImpl implements BollingerPanelRenderer {

    public static final String NAME                = "ai_trading_bollinger_config";
    public static final String BTN_REFRESH         = "boll_refresh";
    public static final String BTN_EDIT_SYMBOL     = "boll_edit_symbol";
    public static final String BTN_TOGGLE_ACTIVE   = "boll_toggle_active";
    public static final String BTN_TOGGLE_LONG     = "boll_toggle_allow_long";
    public static final String BTN_TOGGLE_SHORT    = "boll_toggle_allow_short";
    public static final String BTN_EDIT_ORDER_VOL  = "boll_edit_orderVolume";
    public static final String BTN_EDIT_PERIOD     = "boll_edit_period";
    public static final String BTN_EDIT_STD        = "boll_edit_stdDevMultiplier";
    public static final String BTN_EDIT_TP         = "boll_edit_takeProfitPct";
    public static final String BTN_EDIT_SL         = "boll_edit_stopLossPct";
    public static final String BTN_EDIT_TF         = "boll_edit_timeframe";
    public static final String BTN_EDIT_HISTORY    = "boll_edit_cachedCandlesLimit";
    public static final String BTN_HELP            = "ai_trading_bollinger_help";

    // Новые — управление ордерами без мусора из 10+ кнопок
    public static final String BTN_CANCEL_NEAREST  = "boll_cancel_nearest";
    public static final String BTN_CANCEL_ALL      = "boll_cancel_all";

    private final BollingerStrategySettingsService settingsService;
    private final TradeLogService tradeLogService;
    private final ExchangeOrderDbService orderDb;
    private final MarketLiveService liveService;

    @Override
    public SendMessage render(Long chatId) {
        BollingerStrategySettings s = settingsService.getOrCreate(chatId);
        String symbol = nvl(s.getSymbol());
        LiveSnapshot live = liveService.build(chatId, symbol);

        Optional<TradeLogEntry> lastTradeOpt = tradeLogService.getLastTrade(chatId, symbol);
        String pnlBlock = lastTradeOpt.map(last -> buildPnlBlock(last, symbol, live)).orElse("_нет сделок_");
        String totalPnlBlock = formatTotalPnl(tradeLogService.getTotalPnl(chatId, symbol));

        var openOrders = orderDb.findOpenByChatAndSymbol(chatId, symbol);
        String openOrdersBlock = formatOpenOrdersBlock(openOrders);

        String text = ("""
                *📊 Bollinger Bands Strategy*
                Статус: %s

                *Рынок:* `%s`
                %s Изм.: %s | 💵 Цена: `%s`

                *Баланс:*
                ⚡ %s: `%s`
                💵 %s: `%s`

                *Сделки / PnL:*
                %s
                %s

                *Открытые ордера (%d):*
                %s

                *Параметры:*
                • Объем: `%.4f`
                • Таймфрейм: `%s`
                • История: `%d`
                • SMA период: `%d`
                • Коэф. σ: `%.2f`
                • LONG: %s • SHORT: %s
                • TP: `%.2f%%` • SL: `%.2f%%`
                """).stripTrailing().formatted(
                s.isActive() ? "🟢 *Запущена*" : "🔴 *Остановлена*",
                symbol,
                (live.getChangePct() >= 0 ? "📈" : "📉"),
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
                safeI(s.getPeriod()),
                safeD(s.getStdDevMultiplier()),
                boolEmoji(s.getAllowLong()), boolEmoji(s.getAllowShort()),
                safeD(s.getTakeProfitPct()),
                safeD(s.getStopLossPct())
        );

        // Компактная и понятная раскладка: максимум 3 в ряд, без 10+ «закрыть ордер …»
        InlineKeyboardMarkup markup = AdaptiveKeyboard.markupFromGroups(List.of(
                // 1 ряд
                List.of(
                        AdaptiveKeyboard.btn("‹ Назад", "ai_trading"),
                        AdaptiveKeyboard.btn("⏱ Обновить", BTN_REFRESH),
                        AdaptiveKeyboard.btn("ℹ️ Описание", BTN_HELP)
                ),
                // 2 ряд — навигация по инструменту
                List.of(
                        AdaptiveKeyboard.btn("🎯 Символ", BTN_EDIT_SYMBOL),
                        AdaptiveKeyboard.btn("⏱ ТФ", BTN_EDIT_TF),
                        AdaptiveKeyboard.btn("📋 История", BTN_EDIT_HISTORY)
                ),
                // 3 ряд — основные параметры
                List.of(
                        AdaptiveKeyboard.btn("📐 SMA", BTN_EDIT_PERIOD),
                        AdaptiveKeyboard.btn("σ", BTN_EDIT_STD),
                        AdaptiveKeyboard.btn("💰 Объём %", BTN_EDIT_ORDER_VOL)
                ),
                // 4 ряд — риски
                List.of(
                        AdaptiveKeyboard.btn("🎯 TP %", BTN_EDIT_TP),
                        AdaptiveKeyboard.btn("🛡 SL %", BTN_EDIT_SL)
                ),
                // 5 ряд — направление
                List.of(
                        AdaptiveKeyboard.btn("📈 LONG " + onOff(s.getAllowLong()), BTN_TOGGLE_LONG),
                        AdaptiveKeyboard.btn("📉 SHORT " + onOff(s.getAllowShort()), BTN_TOGGLE_SHORT)
                ),
                // 6 ряд — управление ордерами
                List.of(
                        AdaptiveKeyboard.btn("❌ Ближайший ордер", BTN_CANCEL_NEAREST),
                        AdaptiveKeyboard.btn("🧹 Отменить все", BTN_CANCEL_ALL)
                ),
                // 7 ряд — старт/стоп
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

    /* локальные «безопасные» геттеры на случай null */
    private static int safeI(Integer v) { return v == null ? 0 : v; }
    private static double safeD(Double v) { return v == null ? 0d : v; }
}
