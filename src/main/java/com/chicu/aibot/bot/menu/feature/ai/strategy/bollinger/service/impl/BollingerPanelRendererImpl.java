package com.chicu.aibot.bot.menu.feature.ai.strategy.bollinger.service.impl;

import com.chicu.aibot.bot.menu.feature.ai.strategy.bollinger.service.BollingerPanelRenderer;
import com.chicu.aibot.bot.menu.feature.ai.strategy.view.LiveSnapshot;
import com.chicu.aibot.bot.ui.AdaptiveKeyboard;
import com.chicu.aibot.exchange.order.service.ExchangeOrderDbService;
import com.chicu.aibot.exchange.service.MarketLiveService;
import com.chicu.aibot.strategy.bollinger.model.BollingerStrategySettings;
import com.chicu.aibot.strategy.bollinger.service.BollingerStrategySettingsService;
import com.chicu.aibot.trading.trade.TradeLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

import static com.chicu.aibot.bot.menu.feature.ai.strategy.view.PanelTextUtils.*;

@Component
@RequiredArgsConstructor
public class BollingerPanelRendererImpl implements BollingerPanelRenderer {

    public static final String NAME               = "ai_trading_bollinger_config";
    public static final String BTN_REFRESH        = "boll_refresh";
    public static final String BTN_EDIT_SYMBOL    = "boll_edit_symbol";
    public static final String BTN_TOGGLE_ACTIVE  = "boll_toggle_active";
    public static final String BTN_TOGGLE_LONG    = "boll_toggle_allow_long";
    public static final String BTN_TOGGLE_SHORT   = "boll_toggle_allow_short";
    public static final String BTN_EDIT_ORDER_VOL = "boll_edit_orderVolume";
    public static final String BTN_EDIT_PERIOD    = "boll_edit_period";
    public static final String BTN_EDIT_STD       = "boll_edit_stdDevMultiplier";
    public static final String BTN_EDIT_TP        = "boll_edit_takeProfitPct";
    public static final String BTN_EDIT_SL        = "boll_edit_stopLossPct";
    public static final String BTN_EDIT_TF        = "boll_edit_timeframe";
    public static final String BTN_EDIT_HISTORY   = "boll_edit_cachedCandlesLimit";
    public static final String BTN_HELP           = "ai_trading_bollinger_help";

    private final BollingerStrategySettingsService settingsService;
    private final TradeLogService tradeLogService;
    private final ExchangeOrderDbService orderDb;
    private final MarketLiveService liveService;

    @Override
    public SendMessage render(Long chatId) {
        BollingerStrategySettings s = settingsService.getOrCreate(chatId);
        String symbol = nvl(s.getSymbol());

        LiveSnapshot live = liveService.build(chatId, symbol);
        String pnlBlock   = buildPnlBlock(tradeLogService.getLastTrade(chatId, symbol).orElse(null), symbol, live);

        var openOrders = orderDb.findOpenByChatAndSymbol(chatId, symbol);
        String openOrdersBlock = formatOpenOrdersBlock(openOrders);
        int openCount = openOrders.size();

        String text = ("""
                *📊 Bollinger Bands Strategy*
                %s

                *Рынок:* `%s`
                Изм.: %s  |  Цена: `%s`

                *Баланс:*
                • %s: `%s`
                • %s: `%s`

                *Сделки / PnL:*
                %s

                *Открытые ордера (%d):*
                %s

                *Параметры:*
                • Объем: `%.4f`
                • Таймфрейм: `%s`
                • История: `%d`
                • Период SMA: `%d`
                • Коэф. σ: `%.2f`
                • LONG: %s • SHORT: %s
                • TP: `%.2f%%` • SL: `%.2f%%`
                • Статус: *%s*
                """).stripTrailing().formatted(
                s.isActive() ? "Стратегия: 🟢 *Запущена*" : "Стратегия: 🔴 *Остановлена*",

                symbol,
                live.getChangeStr(),
                live.getPriceStr(),

                live.getBase(),  live.getBaseBal(),
                live.getQuote(), live.getQuoteBal(),

                pnlBlock,

                openCount,
                openOrdersBlock,

                s.getOrderVolume(),
                s.getTimeframe(),
                s.getCachedCandlesLimit(),
                s.getPeriod(),
                s.getStdDevMultiplier(),
                boolEmoji(s.getAllowLong()), boolEmoji(s.getAllowShort()),
                s.getTakeProfitPct(),
                s.getStopLossPct(),
                s.isActive() ? "🟢 Запущена" : "🔴 Остановлена"
        );

        // Короткие подписи + жёстко 2 в ряд (адаптив опустит до 1 при очень длинных)
        List<InlineKeyboardButton> g1 = List.of(
                AdaptiveKeyboard.btn("ℹ️ Описание", BTN_HELP),
                AdaptiveKeyboard.btn("⏱ Обновить", BTN_REFRESH),
                AdaptiveKeyboard.btn("‹ Назад", "ai_trading")
        );
        List<InlineKeyboardButton> g2 = List.of(
                AdaptiveKeyboard.btn("🎯 Символ", BTN_EDIT_SYMBOL),
                AdaptiveKeyboard.btn("💰 Объём %", BTN_EDIT_ORDER_VOL),
                AdaptiveKeyboard.btn("⏱ ТФ", BTN_EDIT_TF),
                AdaptiveKeyboard.btn("📋 История", BTN_EDIT_HISTORY)
        );
        List<InlineKeyboardButton> g3 = List.of(
                AdaptiveKeyboard.btn("📐 SMA", BTN_EDIT_PERIOD),
                AdaptiveKeyboard.btn("Коэф. σ", BTN_EDIT_STD)        );
        List<InlineKeyboardButton> g4 = List.of(
                AdaptiveKeyboard.btn("📈 LONG " + onOff(s.getAllowLong()),  BTN_TOGGLE_LONG),
                AdaptiveKeyboard.btn("📉 SHORT " + onOff(s.getAllowShort()), BTN_TOGGLE_SHORT),
                AdaptiveKeyboard.btn("🎯 TP %", BTN_EDIT_TP),
                AdaptiveKeyboard.btn("🛡 SL %", BTN_EDIT_SL)
        );
        List<InlineKeyboardButton> g5 = List.of(
                AdaptiveKeyboard.btn("▶️ ВКЛ/ВЫКЛ", BTN_TOGGLE_ACTIVE)
        );

        InlineKeyboardMarkup markup = AdaptiveKeyboard.markupFromGroups(List.of(g1, g2, g3, g4, g5), 3);

        return SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode("Markdown")
                .disableWebPagePreview(true)
                .replyMarkup(markup)
                .build();
    }
}
