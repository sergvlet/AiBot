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

    public static final String NAME = "ai_trading_scalping_config";
    public static final String BTN_REFRESH = "scalp_refresh";
    public static final String BTN_EDIT_SYMBOL = "scalp_edit_symbol";
    public static final String BTN_TOGGLE = "scalp_toggle_active";
    public static final String BTN_HELP = "scalp_help";

    private final ScalpingStrategySettingsService settingsService;
    private final MarketLiveService liveService;
    private final TradeLogService tradeLogService;
    private final ExchangeOrderDbService orderDb;

    @Override
    public SendMessage render(Long chatId) {
        ScalpingStrategySettings s = settingsService.getOrCreate(chatId);
        String symbol = nvl(s.getSymbol());
        LiveSnapshot live = liveService.build(chatId, symbol);

        Optional<TradeLogEntry> lastTradeOpt = tradeLogService.getLastTrade(chatId, symbol);
        String pnlBlock = lastTradeOpt.map(last -> buildPnlBlock(last, symbol, live)).orElse("_нет сделок_");
        String totalPnlBlock = formatTotalPnl(tradeLogService.getTotalPnl(chatId, symbol));

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
                live.getChangeStr(),
                live.getPriceStr(),
                live.getBase(), live.getBaseBal(),
                live.getQuote(), live.getQuoteBal(),
                pnlBlock,
                totalPnlBlock,
                openOrders.size(),
                openOrdersBlock,
                s.getOrderVolume(),
                s.getTimeframe(),
                s.getCachedCandlesLimit(),
                s.getWindowSize(),
                s.getPriceChangeThreshold(),
                s.getSpreadThreshold(),
                s.getTakeProfitPct(),
                s.getStopLossPct(),
                s.isActive() ? "🟢 Запущена" : "🔴 Остановлена"
        );

        InlineKeyboardMarkup markup = AdaptiveKeyboard.markupFromGroups(List.of(
                List.of(
                        button("ℹ️ Описание", BTN_HELP),
                        button("⏱ Обновить", BTN_REFRESH)
                ),
                List.of(
                        button("🎯 Символ", BTN_EDIT_SYMBOL),
                        button("⏱ Таймфрейм", "scalp_edit_timeframe"),
                        button("💰 Объём %", "scalp_edit_orderVolume"),
                        button("📋 История", "scalp_edit_cachedCandlesLimit")
                ),
                List.of(
                        button("🪟 Окно", "scalp_edit_windowSize"),
                        button("⚡ ΔЦены %", "scalp_edit_priceChangeThreshold"),
                        button("↔️ Спред %", "scalp_edit_spreadThreshold")
                ),
                List.of(
                        button("🎯 TP %", "scalp_edit_takeProfitPct"),
                        button("🛡 SL %", "scalp_edit_stopLossPct")
                ),
                List.of(
                        button(s.isActive() ? "🔴 Остановить стратегию" : "🟢 Запустить стратегию", BTN_TOGGLE),
                        button("‹ Назад", "ai_trading")
                )
        ));

        return SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode("Markdown")
                .disableWebPagePreview(true)
                .replyMarkup(markup)
                .build();
    }

    private InlineKeyboardButton button(String text, String data) {
        return InlineKeyboardButton.builder().text(text).callbackData(data).build();
    }
}
