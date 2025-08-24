package com.chicu.aibot.bot.menu.feature.ai.strategy.scalping.service.impl;

import com.chicu.aibot.bot.menu.feature.ai.strategy.scalping.service.ScalpingLiveService;
import com.chicu.aibot.bot.menu.feature.ai.strategy.scalping.service.ScalpingPanelRenderer;
import com.chicu.aibot.bot.menu.feature.ai.strategy.view.LiveSnapshot;
import com.chicu.aibot.bot.ui.AdaptiveKeyboard;
import com.chicu.aibot.exchange.order.model.ExchangeOrderEntity;
import com.chicu.aibot.exchange.order.service.ExchangeOrderDbService;
import com.chicu.aibot.strategy.scalping.model.ScalpingStrategySettings;
import com.chicu.aibot.strategy.scalping.service.ScalpingStrategySettingsService;
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
public class ScalpingPanelRendererImpl implements ScalpingPanelRenderer {

    public static final String NAME            = "ai_trading_scalping_config";
    public static final String BTN_REFRESH     = "scalp_refresh";
    public static final String BTN_EDIT_SYMBOL = "edit_symbol";
    public static final String BTN_TOGGLE      = "scalp_toggle_active";
    public static final String BTN_HELP        = "scalp_help";

    private final ScalpingStrategySettingsService settingsService;
    private final ScalpingLiveService liveService;
    private final TradeLogService tradeLogService;
    private final ExchangeOrderDbService orderDb;

    @Override
    public SendMessage render(Long chatId) {
        ScalpingStrategySettings s = settingsService.getOrCreate(chatId);
        String symbol = nvl(s.getSymbol());

        LiveSnapshot live = liveService.build(chatId, symbol);

        var lastTrade = tradeLogService.getLastTrade(chatId, symbol).orElse(null);
        String pnlBlock = buildPnlBlock(lastTrade, symbol, live);

        List<ExchangeOrderEntity> openOrders = orderDb.findOpenByChatAndSymbol(chatId, symbol);
        String openOrdersBlock = formatOpenOrdersBlock(openOrders);
        int openCount = openOrders.size();

        String text = ("""
                *📊 Scalping Strategy*
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
                • История: `%d` • Окно: `%d`
                • ΔЦены: `%.2f%%` • Макс. спред: `%.2f%%`
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
                s.getWindowSize(),
                s.getPriceChangeThreshold(),
                s.getSpreadThreshold(),
                s.getTakeProfitPct(),
                s.getStopLossPct(),
                s.isActive() ? "🟢 Запущена" : "🔴 Остановлена"
        );

        // ——— КНОПКИ ———
        var g1 = List.of(
                button("ℹ️ Описание стратегии", BTN_HELP),
                button("⏱ Обновить", BTN_REFRESH)
        );
        var g2 = List.of(
                button("🎯 Символ", BTN_EDIT_SYMBOL),
                button("⏱ Таймфрейм", "scalp_edit_timeframe"),
                button("💰 Объём сделки, %", "scalp_edit_orderVolume"),
                button("📋 История", "scalp_edit_cachedCandlesLimit")
        );
        var g3 = List.of(
                button("🪟 Окно", "scalp_edit_windowSize"),
                button("⚡ Триггер входа, %", "scalp_edit_priceChangeThreshold"),
                button("↔️ Макс. спред, %", "scalp_edit_spreadThreshold")
        );
        var g4 = List.of(
                button("🎯 Тейк-профит, %", "scalp_edit_takeProfitPct"),
                button("🛡 Стоп-лосс, %", "scalp_edit_stopLossPct")
        );
        var g5 = List.of(
                button("▶️ Стратегия: ВКЛ/ВЫКЛ", BTN_TOGGLE),
                button("‹ Назад", "ai_trading")
        );

        InlineKeyboardMarkup markup = AdaptiveKeyboard.markupFromGroups(List.of(g1, g2, g3, g4, g5));

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
