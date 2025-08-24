package com.chicu.aibot.bot.menu.feature.ai.strategy.fibonacciGrid.service.impl;

import com.chicu.aibot.bot.menu.feature.ai.strategy.fibonacciGrid.service.FibonacciGridPanelRenderer;
import com.chicu.aibot.bot.menu.feature.ai.strategy.scalping.service.ScalpingLiveService;
import com.chicu.aibot.bot.menu.feature.ai.strategy.view.LiveSnapshot;
import com.chicu.aibot.bot.ui.AdaptiveKeyboard;
import com.chicu.aibot.exchange.order.model.ExchangeOrderEntity;
import com.chicu.aibot.exchange.order.service.ExchangeOrderDbService;
import com.chicu.aibot.strategy.fibonacci.model.FibonacciGridStrategySettings;
import com.chicu.aibot.strategy.fibonacci.service.FibonacciGridStrategySettingsService;
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
public class FibonacciGridPanelRendererImpl implements FibonacciGridPanelRenderer {

    public static final String NAME               = "ai_trading_fibonacci_config";
    public static final String BTN_REFRESH        = "fib_refresh";
    public static final String BTN_EDIT_SYMBOL    = "fib_edit_symbol";
    public static final String BTN_TOGGLE_ACTIVE  = "fib_toggle_active";
    public static final String BTN_TOGGLE_LONG    = "fib_toggle_allow_long";
    public static final String BTN_TOGGLE_SHORT   = "fib_toggle_allow_short";
    public static final String BTN_EDIT_ORDER_VOL = "fib_edit_orderVolume";
    public static final String BTN_EDIT_GRID      = "fib_edit_gridSizePct";
    public static final String BTN_EDIT_MAX_ORD   = "fib_edit_maxActiveOrders";
    public static final String BTN_EDIT_TP        = "fib_edit_takeProfitPct";
    public static final String BTN_EDIT_SL        = "fib_edit_stopLossPct";
    public static final String BTN_EDIT_TF        = "fib_edit_timeframe";
    public static final String BTN_HELP           = "fib_help";

    private final FibonacciGridStrategySettingsService settingsService;
    private final ScalpingLiveService liveService;          // универсальный снапшот по символу
    private final TradeLogService tradeLogService;
    private final ExchangeOrderDbService orderDb;

    @Override
    public SendMessage render(Long chatId) {
        FibonacciGridStrategySettings s = settingsService.getOrCreate(chatId);
        String symbol = nvl(s.getSymbol());

        LiveSnapshot live = liveService.build(chatId, symbol);

        // общий PnL-блок (через утилиту)
        var lastTrade = tradeLogService.getLastTrade(chatId, symbol).orElse(null);
        String pnlBlock = buildPnlBlock(lastTrade, symbol, live);

        // открытые ордера — общее форматирование
        List<ExchangeOrderEntity> openOrders = orderDb.findOpenByChatAndSymbol(chatId, symbol);
        String openOrdersBlock = formatOpenOrdersBlock(openOrders);
        int openCount = openOrders.size();

        String text = ("""
                *📊 Fibonacci Grid Strategy*
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
                • Шаг сетки: `%.4f%%`
                • Макс. активных ордеров: `%d`
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
                s.getGridSizePct(),
                s.getMaxActiveOrders(),
                boolEmoji(s.getAllowLong()), boolEmoji(s.getAllowShort()),
                s.getTakeProfitPct(),
                s.getStopLossPct(),
                s.isActive() ? "🟢 Запущена" : "🔴 Остановлена"
        );

        InlineKeyboardMarkup markup = buildKeyboard(s);

        return SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode("Markdown")
                .disableWebPagePreview(true)
                .replyMarkup(markup)
                .build();
    }

    // ---------- UI helpers ----------

    private InlineKeyboardMarkup buildKeyboard(FibonacciGridStrategySettings s) {
        List<InlineKeyboardButton> g1 = List.of(
                btn("ℹ️ Описание стратегии", BTN_HELP),
                btn("⏱ Обновить", BTN_REFRESH),
                btn("‹ Назад", "ai_trading")
        );
        List<InlineKeyboardButton> g2 = List.of(
                btn("🎯 Символ", BTN_EDIT_SYMBOL),
                btn("💰 Объём, %", BTN_EDIT_ORDER_VOL),
                btn("🧱 Шаг сетки, %", BTN_EDIT_GRID)
        );
        List<InlineKeyboardButton> g3 = List.of(
                btn("📊 Макс. ордеров", BTN_EDIT_MAX_ORD),
                btn("🎯 TP, %", BTN_EDIT_TP),
                btn("🛡 SL, %", BTN_EDIT_SL)
        );
        List<InlineKeyboardButton> g4 = List.of(
                btn("📈 LONG " + onOff(s.getAllowLong()), BTN_TOGGLE_LONG),
                btn("📉 SHORT " + onOff(s.getAllowShort()), BTN_TOGGLE_SHORT),
                btn("⏱ Таймфрейм", BTN_EDIT_TF)
        );
        List<InlineKeyboardButton> g5 = List.of(
                btn("▶️ Стратегия: ВКЛ/ВЫКЛ", BTN_TOGGLE_ACTIVE)
        );

        return AdaptiveKeyboard.markupFromGroups(List.of(g1, g2, g3, g4, g5));
    }

    private InlineKeyboardButton btn(String text, String data) {
        return InlineKeyboardButton.builder().text(text).callbackData(data).build();
    }
}
