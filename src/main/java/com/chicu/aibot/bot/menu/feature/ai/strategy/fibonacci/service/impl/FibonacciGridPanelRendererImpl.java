package com.chicu.aibot.bot.menu.feature.ai.strategy.fibonacci.service.impl;

import com.chicu.aibot.bot.menu.feature.ai.strategy.fibonacci.service.FibonacciGridPanelRenderer;
import com.chicu.aibot.bot.menu.feature.ai.strategy.view.LiveSnapshot;
import com.chicu.aibot.bot.ui.AdaptiveKeyboard;
import com.chicu.aibot.exchange.order.model.ExchangeOrderEntity;
import com.chicu.aibot.exchange.order.service.ExchangeOrderDbService;
import com.chicu.aibot.exchange.service.MarketLiveService;
import com.chicu.aibot.strategy.fibonacci.model.FibonacciGridStrategySettings;
import com.chicu.aibot.strategy.fibonacci.service.FibonacciGridStrategySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static com.chicu.aibot.bot.menu.feature.ai.strategy.view.PanelTextUtils.boolEmoji;
import static com.chicu.aibot.bot.menu.feature.ai.strategy.view.PanelTextUtils.nvl;
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

    // Кнопки управления ордерами
    public static final String BTN_CANCEL_ORDER_PREFIX = "fib_cancel_order:"; // + orderId
    public static final String BTN_CANCEL_ALL          = "fib_cancel_all";

    // Ограничения раскладки
    private static final int MAX_ORDER_BUTTONS = 3; // не плодим 10+ кнопок
    private static final int MAX_COLUMNS = 2;       // удобнее попадать по кнопкам

    private final FibonacciGridStrategySettingsService settingsService;
    private final MarketLiveService liveService;
    private final ExchangeOrderDbService orderDb;

    @Override
    public SendMessage render(Long chatId) {
        FibonacciGridStrategySettings s = settingsService.getOrCreate(chatId);
        String symbol = nvl(s.getSymbol());
        LiveSnapshot live = liveService.build(chatId, symbol);

        // Сделки / PnL (на основе FILLED из БД)
        String quote = nvl2(live.getQuote(), "USDT");
        String dealsLine   = buildLastFilledLine(chatId, symbol, quote);
        String totalPnlLine= "Всего по %s: %s %s".formatted(symbol, signMoney(sumFilledPnl(chatId, symbol)), quote);

        // Открытые ордера
        List<ExchangeOrderEntity> openOrders = orderDb.findOpenByChatAndSymbol(chatId, symbol);
        String openOrdersBlock = formatOpenOrdersBlock(openOrders);

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
                nvl(live.getChangeStr()),
                nvl(live.getPriceStr()),
                nvl(live.getBase()), nvl(live.getBaseBal()),
                nvl(live.getQuote()), nvl(live.getQuoteBal()),
                dealsLine, totalPnlLine,
                openOrders.size(), openOrdersBlock,
                safeD(s.getOrderVolume()),
                nvl(s.getTimeframe()),
                safeI(s.getCachedCandlesLimit()),
                safeD(s.getGridSizePct()),
                safeI(s.getMaxActiveOrders()),
                boolEmoji(s.getAllowLong()), boolEmoji(s.getAllowShort()),
                safeD(s.getTakeProfitPct()),
                safeD(s.getStopLossPct()),
                s.isActive() ? "🟢 Запущена" : "🔴 Остановлена"
        );

        // ---------- Клавиатура (компактная, по 2 в ряд) ----------
        var groups = new java.util.ArrayList<java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>>();

        // Верхние кнопки
        groups.add(AdaptiveKeyboard.row(
                AdaptiveKeyboard.btn("ℹ️ Описание", BTN_HELP),
                AdaptiveKeyboard.btn("⏱ Обновить", BTN_REFRESH)
        ));
        groups.add(AdaptiveKeyboard.row(
                AdaptiveKeyboard.btn("‹ Назад", "ai_trading")
        ));

        // Настройки (2 в ряд)
        groups.add(AdaptiveKeyboard.row(
                AdaptiveKeyboard.btn("🎯 Символ", BTN_EDIT_SYMBOL),
                AdaptiveKeyboard.btn("💰 Объём %", BTN_EDIT_ORDER_VOL)
        ));
        groups.add(AdaptiveKeyboard.row(
                AdaptiveKeyboard.btn("🧱 Шаг %", BTN_EDIT_GRID),
                AdaptiveKeyboard.btn("📊 Макс. орд.", BTN_EDIT_MAX_ORD)
        ));
        groups.add(AdaptiveKeyboard.row(
                AdaptiveKeyboard.btn("🎯 TP %", BTN_EDIT_TP),
                AdaptiveKeyboard.btn("🛡 SL %", BTN_EDIT_SL)
        ));
        groups.add(AdaptiveKeyboard.row(
                AdaptiveKeyboard.btn("⏱ Таймфрейм", BTN_EDIT_TF)
        ));

        // Тумблеры
        groups.add(AdaptiveKeyboard.row(
                AdaptiveKeyboard.btn("📈 LONG " + onOff(s.getAllowLong()), BTN_TOGGLE_LONG),
                AdaptiveKeyboard.btn("📉 SHORT " + onOff(s.getAllowShort()), BTN_TOGGLE_SHORT)
        ));

        // Запуск / Стоп
        groups.add(AdaptiveKeyboard.row(
                AdaptiveKeyboard.btn(s.isActive() ? "🔴 Остановить" : "🟢 Запустить", BTN_TOGGLE_ACTIVE)
        ));

        // Управление ордерами
        if (!openOrders.isEmpty()) {
            groups.add(AdaptiveKeyboard.row(
                    AdaptiveKeyboard.btn("❌ Закрыть все", BTN_CANCEL_ALL)
            ));

            var sorted = openOrders.stream()
                    .sorted(Comparator.comparing(ExchangeOrderEntity::getPrice,
                            java.util.Comparator.nullsLast(BigDecimal::compareTo)))
                    .limit(MAX_ORDER_BUTTONS)
                    .toList();

            for (ExchangeOrderEntity o : sorted) {
                String id = nvl2(o.getOrderId(), "-");
                groups.add(AdaptiveKeyboard.row(
                        AdaptiveKeyboard.btn("❌ Закрыть ордер " + id, BTN_CANCEL_ORDER_PREFIX + id)
                ));
            }

            if (openOrders.size() > MAX_ORDER_BUTTONS) {
                groups.add(AdaptiveKeyboard.row(
                        AdaptiveKeyboard.btn("… ещё ордера (обновить)", BTN_REFRESH)
                ));
            }
        }

        InlineKeyboardMarkup markup = AdaptiveKeyboard.markupFromGroups(groups, MAX_COLUMNS);

        return SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode("Markdown")
                .disableWebPagePreview(true)
                .replyMarkup(markup)
                .build();
    }

    /* ==================== Helpers ==================== */

    /** Последний FILLED как строка. */
    private String buildLastFilledLine(Long chatId, String symbol, String quote) {
        List<ExchangeOrderEntity> lastFilled = orderDb.findRecentFilled(chatId, symbol, 1);
        if (lastFilled == null || lastFilled.isEmpty()) return "_нет сделок_";
        ExchangeOrderEntity e = lastFilled.getFirst();

        String side = nvl(e.getSide()).toUpperCase(Locale.ROOT);
        String qty  = fmtQty(e.getQuantity());
        String px   = fmtPrice(e.getPrice());
        String pnl  = signMoney(e.getPnl());
        String pct  = signPct(e.getPnlPct());
        return "Последняя: %s %s @ %s | PnL: %s %s (%s)".formatted(side, qty, px, pnl, quote, pct);
    }

    /** Сумма PnL по последним FILLED (до 1000). */
    private BigDecimal sumFilledPnl(Long chatId, String symbol) {
        List<ExchangeOrderEntity> recent = orderDb.findRecentFilled(chatId, symbol, 1000);
        if (recent == null || recent.isEmpty()) return BigDecimal.ZERO;
        return recent.stream()
                .map(ExchangeOrderEntity::getPnl)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String formatOpenOrdersBlock(List<ExchangeOrderEntity> open) {
        if (open == null || open.isEmpty()) return "_нет_";
        var fmt = new StringBuilder();
        var sorted = open.stream()
                .sorted(Comparator.comparing(ExchangeOrderEntity::getPrice,
                        java.util.Comparator.nullsLast(BigDecimal::compareTo)))
                .toList();
        for (ExchangeOrderEntity o : sorted) {
            String side   = nvl(o.getSide());
            String type   = nvl(o.getType());
            String qty    = fmtQty(o.getQuantity());
            String price  = fmtPrice(o.getPrice());
            String filled = fmtQty(o.getExecutedQty() == null ? BigDecimal.ZERO : o.getExecutedQty());
            String status = "*" + nvl2(o.getStatus(), "NEW") + "*";
            String id     = "#" + nvl2(o.getOrderId(), "-");
            fmt.append("• ").append(side).append(' ').append(type)
               .append(" qty `").append(qty).append('`')
               .append(" @ `").append(price).append('`')
               .append(" filled `").append(filled).append('`')
               .append(' ').append(status).append(' ')
               .append('(').append(id).append(')').append('\n');
        }
        return fmt.toString().stripTrailing();
    }

    private static String nvl2(String v, String def) { return (v == null || v.isBlank()) ? def : v; }
    private static int safeI(Integer v) { return v == null ? 0 : v; }
    private static double safeD(Double v) { return v == null ? 0d : v; }

    private static final ThreadLocal<DecimalFormat> DF_QTY = ThreadLocal.withInitial(() -> {
        DecimalFormatSymbols s = DecimalFormatSymbols.getInstance(Locale.US);
        s.setGroupingSeparator(' ');
        DecimalFormat df = new DecimalFormat("0.########", s);
        df.setGroupingUsed(true);
        df.setMaximumFractionDigits(8);
        return df;
    });
    private static final ThreadLocal<DecimalFormat> DF_PRICE = ThreadLocal.withInitial(() -> {
        DecimalFormatSymbols s = DecimalFormatSymbols.getInstance(Locale.US);
        s.setGroupingSeparator(' ');
        DecimalFormat df = new DecimalFormat("0.####", s);
        df.setGroupingUsed(true);
        df.setMaximumFractionDigits(8);
        return df;
    });
    private static final ThreadLocal<DecimalFormat> DF_MONEY = ThreadLocal.withInitial(() -> {
        DecimalFormatSymbols s = DecimalFormatSymbols.getInstance(Locale.US);
        s.setGroupingSeparator(' ');
        DecimalFormat df = new DecimalFormat("0.##", s);
        df.setGroupingUsed(true);
        df.setMaximumFractionDigits(8);
        return df;
    });

    private static String fmtQty(BigDecimal v)   { return v == null ? "-" : DF_QTY.get().format(v); }
    private static String fmtPrice(BigDecimal v) { return v == null ? "-" : DF_PRICE.get().format(v); }
    private static String fmtMoney(BigDecimal v) { return v == null ? "-" : DF_MONEY.get().format(v); }

    private static String signMoney(BigDecimal v) {
        if (v == null) return "-";
        String s = fmtMoney(v.abs());
        return (v.signum() >= 0 ? "+" : "−") + s;
    }
    private static String signPct(BigDecimal pct) {
        if (pct == null) return "-";
        BigDecimal a = pct.abs();
        String s = DF_MONEY.get().format(a);
        return (pct.signum() >= 0 ? "+" : "−") + s + "%";
    }
}
