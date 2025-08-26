package com.chicu.aibot.bot.menu.feature.ai.strategy.view;

import com.chicu.aibot.exchange.order.model.ExchangeOrderEntity;
import com.chicu.aibot.trading.trade.model.TradeLogEntry;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

public final class PanelTextUtils {
    private PanelTextUtils() {}

    public static String nvl(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }

    public static String boolEmoji(Boolean b) {
        return Boolean.TRUE.equals(b) ? "✅" : "❌";
    }

    public static String onOff(Boolean b) {
        return Boolean.TRUE.equals(b) ? "ON" : "OFF";
    }

    public static String signedPct(double v) {
        return String.format("%s%.2f%%", (v >= 0 ? "+" : "-"), Math.abs(v));
    }

    public static String formatMoneyAbs(double v, String quote) {
        String s = String.format("%,.2f", Math.abs(v));
        return s + " " + quote;
    }

    /** PnL для последней сделки */
    public static String buildPnlBlock(TradeLogEntry last, String symbol, LiveSnapshot live) {
        if (last == null) return "_нет сделок_";

        double entry = last.getEntryPrice() != null ? last.getEntryPrice().doubleValue() : 0.0;
        double qty   = last.getVolume() != null ? last.getVolume().doubleValue() : 0.0;
        double now   = live.getLastPrice();

        double pnlAbs;
        double pnlPct;
        if ("BUY".equalsIgnoreCase(last.getSide())) {
            pnlAbs = (now - entry) * qty;
            pnlPct = entry > 0 ? (now - entry) / entry * 100.0 : 0.0;
        } else if ("SELL".equalsIgnoreCase(last.getSide())) {
            pnlAbs = (entry - now) * qty;
            pnlPct = entry > 0 ? (entry - now) / entry * 100.0 : 0.0;
        } else {
            pnlAbs = 0.0;
            pnlPct = 0.0;
        }

        String dirEmoji = pnlAbs >= 0 ? "🟢" : "🔴";
        String investedS = formatMoneyAbs(entry * qty, live.getQuote());
        String pnlAbsS   = formatMoneyAbs(pnlAbs, live.getQuote());
        String pnlPctS   = signedPct(pnlPct);

        return ("""
               • Последняя: *%s* %s @`%.8f` qty `%.6f`
               • Вложено: `%s`
               • PnL: %s `%s` (%s)
               """).stripTrailing().formatted(
                last.getSide(), symbol, entry, qty,
                investedS,
                dirEmoji, pnlAbsS, pnlPctS
        );
    }

    /** Суммарный PnL */
    public static String formatTotalPnl(Optional<Double> pnlOpt) {
        String result = "_нет сделок_";
        if (pnlOpt.isPresent()) {
            double pnl = pnlOpt.get();
            result = String.format("💰 Всего PnL: %+.2f USDT", pnl);
        }
        return result;
    }

    /** Блок открытых ордеров */
    public static String formatOpenOrdersBlock(Collection<ExchangeOrderEntity> openOrders) {
        if (openOrders == null || openOrders.isEmpty()) return "_нет ордеров_";
        return openOrders.stream()
                .map(PanelTextUtils::formatOpenOrder)
                .collect(Collectors.joining("\n"));
    }

    private static String formatOpenOrder(ExchangeOrderEntity e) {
        String side   = e.getSide();
        String type   = e.getType();
        BigDecimal price   = e.getPrice();
        BigDecimal qty     = e.getQuantity();
        BigDecimal filled  = e.getExecutedQty();
        String status = e.getStatus();

        String priceS  = (price == null)  ? "MKT" : String.format("%.8f", price);
        String filledS = (filled == null) ? "0"   : filled.stripTrailingZeros().toPlainString();
        String qtyS    = (qty == null)    ? "?"   : qty.stripTrailingZeros().toPlainString();

        return String.format("• %s %s qty `%s` @ `%s` filled `%s` *%s* (#%s)",
                side, type, qtyS, priceS, filledS, status, e.getOrderId());
    }
}
