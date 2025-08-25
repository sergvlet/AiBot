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
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;
import java.util.Optional;

import static com.chicu.aibot.bot.menu.feature.ai.strategy.view.PanelTextUtils.*;

@Component
@RequiredArgsConstructor
public class ScalpingPanelRendererImpl implements ScalpingPanelRenderer {

    public static final String NAME            = "ai_trading_scalping_config";
    public static final String BTN_REFRESH     = "scalp_refresh";
    public static final String BTN_EDIT_SYMBOL = "scalp_edit_symbol";
    public static final String BTN_TOGGLE      = "scalp_toggle_active";
    public static final String BTN_HELP        = "scalp_help";

    private final ScalpingStrategySettingsService settingsService;
    private final MarketLiveService liveService;
    private final TradeLogService tradeLogService;
    private final ExchangeOrderDbService orderDb;

    @Override
    public SendMessage render(Long chatId) {
        ScalpingStrategySettings s = settingsService.getOrCreate(chatId);
        String symbol = nvl(s.getSymbol());

        LiveSnapshot live = liveService.build(chatId, symbol);

        // ===== Сделки / PnL =====
        Optional<TradeLogEntry> lastTradeOpt = tradeLogService.getLastTrade(chatId, symbol);
        String pnlBlock = lastTradeOpt
                .map(last -> buildPnlBlock(last, symbol, live))
                .orElse("_нет сделок_");

        double totalPnl = tradeLogService.getTotalPnl(chatId, symbol).orElse(0.0);

        // ===== Ордера =====
        List<ExchangeOrderEntity> openOrders = orderDb.findOpenByChatAndSymbol(chatId, symbol);
        String openOrdersBlock = formatOpenOrdersBlock(openOrders);

        // ===== Основной текст =====
        String text = ("""
                *📊 Scalping Strategy*
                Статус: %s

                *Рынок:* `%s`
                %s Изм.: %s | 💵 Цена: `%s`

                *Баланс:*
                ⚡ %s: `%s`
                💵 %s: `%s`

                *Сделки / PnL:*
                %s
                💰 Всего PnL: %+.2f USDT

                *Открытые ордера (%d):*
                %s

                *Параметры:*
                • Объем: `%.4f`
                • Таймфрейм: `%s`
                • История: `%d`
                • Окно: `%d`
                • ΔЦены: `%.2f%%`
                • Макс. спред: `%.2f%%`
                • TP: `%.2f%%` • SL: `%.2f%%`
                """).stripTrailing().formatted(
                s.isActive() ? "🟢 *Запущена*" : "🔴 *Остановлена*",

                symbol,
                live.getChangePct() >= 0 ? "📈" : "📉",
                live.getChangeStr(),
                live.getPriceStr(),

                live.getBase(), live.getBaseBal(),
                live.getQuote(), live.getQuoteBal(),

                pnlBlock,
                totalPnl,

                openOrders.size(),
                openOrdersBlock,

                s.getOrderVolume(),
                s.getTimeframe(),
                s.getCachedCandlesLimit(),
                s.getWindowSize(),
                s.getPriceChangeThreshold(),
                s.getSpreadThreshold(),
                s.getTakeProfitPct(),
                s.getStopLossPct()
        );

        // ===== Кнопки =====
        List<InlineKeyboardButton> g1 = List.of(
                AdaptiveKeyboard.btn("ℹ️ Описание", BTN_HELP),
                AdaptiveKeyboard.btn("⏱ Обновить", BTN_REFRESH),
                AdaptiveKeyboard.btn("‹ Назад", "ai_trading")
        );
        List<InlineKeyboardButton> g2 = List.of(
                AdaptiveKeyboard.btn("🎯 Символ", BTN_EDIT_SYMBOL),
                AdaptiveKeyboard.btn("💰 Объём %", "scalp_edit_orderVolume"),
                AdaptiveKeyboard.btn("⏱ Таймфрейм", "scalp_edit_timeframe"),
                AdaptiveKeyboard.btn("📋 История", "scalp_edit_cachedCandlesLimit")
        );
        List<InlineKeyboardButton> g3 = List.of(
                AdaptiveKeyboard.btn("🪟 Окно", "scalp_edit_windowSize"),
                AdaptiveKeyboard.btn("⚡ ΔЦены %", "scalp_edit_priceChangeThreshold"),
                AdaptiveKeyboard.btn("↔️ Спред %", "scalp_edit_spreadThreshold")
        );
        List<InlineKeyboardButton> g4 = List.of(
                AdaptiveKeyboard.btn("🎯 TP %", "scalp_edit_takeProfitPct"),
                AdaptiveKeyboard.btn("🛡 SL %", "scalp_edit_stopLossPct")
        );
        List<InlineKeyboardButton> g5 = List.of(
                AdaptiveKeyboard.btn(s.isActive() ? "🔴 Остановить стратегию" : "🟢 Запустить стратегию", BTN_TOGGLE)
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
