package com.chicu.aibot.bot.menu.feature.ai.strategy.fibonacci.service.impl;

import com.chicu.aibot.bot.menu.feature.ai.strategy.fibonacci.service.FibonacciGridPanelRenderer;
import com.chicu.aibot.bot.menu.feature.ai.strategy.view.LiveSnapshot;
import com.chicu.aibot.bot.ui.AdaptiveKeyboard;
import com.chicu.aibot.exchange.order.model.ExchangeOrderEntity;
import com.chicu.aibot.exchange.order.service.ExchangeOrderDbService;
import com.chicu.aibot.exchange.service.MarketLiveService;
import com.chicu.aibot.strategy.fibonacci.model.FibonacciGridStrategySettings;
import com.chicu.aibot.strategy.fibonacci.service.FibonacciGridStrategySettingsService;
import com.chicu.aibot.trading.trade.TradeLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.text.DecimalFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.chicu.aibot.bot.menu.feature.ai.strategy.view.PanelTextUtils.boolEmoji;
import static com.chicu.aibot.bot.menu.feature.ai.strategy.view.PanelTextUtils.onOff;

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
    private final MarketLiveService liveService;
    private final TradeLogService tradeLogService;
    private final ExchangeOrderDbService orderDb;

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("dd.MM HH:mm")
                    .withLocale(Locale.forLanguageTag("ru-RU"))
                    .withZone(ZoneId.systemDefault());

    @Override
    public SendMessage render(Long chatId) {
        FibonacciGridStrategySettings s = settingsService.getOrCreate(chatId);
        String symbol = nvl(s.getSymbol());
        LiveSnapshot live = liveService.build(chatId, symbol);

        // Лёгкий текст вместо “последней сделки” (реальные сделки показываем ниже блоком FILLED)
        String recentHint = "_см. ниже блок «Последние исполненные ордера»_";

        // Итоговый PnL — принимаем Optional<?> (поддержит Optional<Double> и Optional<String>)
        String totalPnlBlock = formatTotalPnl(tradeLogService.getTotalPnl(chatId, symbol));

        // Открытые ордера
        List<ExchangeOrderEntity> openOrders = orderDb.findOpenByChatAndSymbol(chatId, symbol);
        String openOrdersBlock = formatOpenOrdersBlock(openOrders);

        // Недавние исполненные ордера
        String recentTradesBlock = formatRecentFills(chatId, symbol, 5);

        String text = ("""
                *📊 Fibonacci Grid Strategy*
                %s

                *Рынок:* `%s`
                %s Изм.: %s | 💵 Цена: `%s`

                *Баланс:*
                • %s: `%s`
                • %s: `%s`

                *Сделки / PnL:*
                %s
                %s

                *Последние исполненные ордера:*
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
                s.isActive() ? "🟢 *Запущена*" : "🔴 *Остановлена*",
                symbol,
                live.getChangePct() >= 0 ? "📈" : "📉",
                live.getChangeStr(),
                live.getPriceStr(),
                live.getBase(), live.getBaseBal(),
                live.getQuote(), live.getQuoteBal(),
                recentHint,
                totalPnlBlock,
                recentTradesBlock,
                openOrders.size(),
                openOrdersBlock,
                nvl(s.getOrderVolume(), 0.0),
                nvl(s.getTimeframe(), "1m"),
                nvl(s.getCachedCandlesLimit(), 500),
                nvl(s.getGridSizePct(), 0.8),
                nvl(s.getMaxActiveOrders(), 3),
                boolEmoji(s.getAllowLong()), boolEmoji(s.getAllowShort()),
                nvl(s.getTakeProfitPct(), 0.6),
                nvl(s.getStopLossPct(), 0.8),
                s.isActive() ? "🟢 Запущена" : "🔴 Остановлена"
        );

        InlineKeyboardMarkup markup = AdaptiveKeyboard.markupFromGroups(List.of(
                List.of(
                        AdaptiveKeyboard.btn("ℹ️ Описание", BTN_HELP),
                        AdaptiveKeyboard.btn("⏱ Обновить", BTN_REFRESH),
                        AdaptiveKeyboard.btn("‹ Назад", "ai_trading")
                ),
                List.of(
                    AdaptiveKeyboard.btn("🎯 Символ", BTN_EDIT_SYMBOL),
                    AdaptiveKeyboard.btn("💰 Объём %", BTN_EDIT_ORDER_VOL),
                    AdaptiveKeyboard.btn("🧱 Шаг %", BTN_EDIT_GRID),
                    AdaptiveKeyboard.btn("📊 Макс. орд.", BTN_EDIT_MAX_ORD)
                ),
                List.of(
                    AdaptiveKeyboard.btn("📈 LONG " + onOff(s.getAllowLong()), BTN_TOGGLE_LONG),
                    AdaptiveKeyboard.btn("📉 SHORT " + onOff(s.getAllowShort()), BTN_TOGGLE_SHORT),
                    AdaptiveKeyboard.btn("🎯 TP %", BTN_EDIT_TP),
                    AdaptiveKeyboard.btn("🛡 SL %", BTN_EDIT_SL),
                    AdaptiveKeyboard.btn("⏱ Таймфрейм", BTN_EDIT_TF)
                ),
                List.of(
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

    /* ================= helpers ================= */

    /** Печать последних FILLED из БД. */
    private String formatRecentFills(Long chatId, String symbol, int limit) {
        // Сервисный метод ожидаем такого вида:
        //   List<ExchangeOrderEntity> findRecentFilled(Long chatId, String symbol, int limit)
        List<ExchangeOrderEntity> filled = orderDb.findRecentFilled(chatId, symbol, limit);
        if (filled == null || filled.isEmpty()) return "_нет сделок_";

        StringBuilder sb = new StringBuilder();
        for (ExchangeOrderEntity o : filled) {
            String ts = (o.getUpdatedAt() != null) ? TS_FMT.format(o.getUpdatedAt()) : "—";
            sb.append("• ").append(nvl(o.getSide()))
              .append(" `").append(nz(o.getQuantity())).append('`')
              .append(" @ `").append(nz(o.getPrice())).append('`')
              .append(" *FILLED* ")
              .append("(`#").append(nvl(o.getOrderId())).append("`)")
              .append(" _( ").append(ts).append(" )_")
              .append('\n');
        }
        return sb.toString().stripTrailing();
    }

    /** Открытые ордера. */
    private static String formatOpenOrdersBlock(List<ExchangeOrderEntity> open) {
        if (open == null || open.isEmpty()) return "_нет открытых ордеров_";
        StringBuilder sb = new StringBuilder();
        for (ExchangeOrderEntity o : open) {
            sb.append("• ")
              .append(nvl(o.getSide())).append(' ').append(nvl(o.getType()))
              .append(" qty `").append(nz(o.getQuantity())).append('`')
              .append(" @ `").append(nz(o.getPrice())).append('`')
              .append(" filled `").append(nz(o.getExecutedQty())).append('`')
              .append(' ').append(nvl(o.getStatus()))
              .append(" (`#").append(nvl(o.getOrderId())).append("`)")
              .append('\n');
        }
        return sb.toString().stripTrailing();
    }

    /** Форматируем итоговый PnL из Optional<Double> или Optional<String>. */
    private static String formatTotalPnl(Optional<?> totalPnlOpt) {
        if (totalPnlOpt == null || totalPnlOpt.isEmpty()) return "_нет данных по PnL_";
        Object v = totalPnlOpt.get();
        if (v instanceof Number num) {
            // красиво отформатируем число
            DecimalFormat df = (DecimalFormat) DecimalFormat.getNumberInstance(Locale.forLanguageTag("ru-RU"));
            df.applyPattern("#,##0.##");
            return "*Итоговый PnL:* " + df.format(num.doubleValue());
        }
        return "*Итоговый PnL:* " + v;
    }

    // безопасные mini-NVL’ы (перегрузки)
    private static String nvl(String s) { return (s == null) ? "" : s; }
    private static String nvl(String s, String def) { return (s == null || s.isBlank()) ? def : s; }
    private static int nvl(Integer v, int def) { return v == null ? def : v; }
    private static double nvl(Double v, double def) { return v == null ? def : v; }
    private static String nz(Object v) { return v == null ? "0" : v.toString(); }
}
