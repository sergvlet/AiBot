package com.chicu.aibot.strategy.bollinger;

import com.chicu.aibot.bot.menu.feature.ai.strategy.view.LiveSnapshot;
import com.chicu.aibot.exchange.service.MarketLiveService;
import com.chicu.aibot.strategy.StrategyType;
import com.chicu.aibot.strategy.TradingStrategy;
import com.chicu.aibot.strategy.bollinger.model.BollingerStrategySettings;
import com.chicu.aibot.strategy.bollinger.service.BollingerStrategySettingsService;
import com.chicu.aibot.strategy.model.Candle;
import com.chicu.aibot.strategy.model.Order;
import com.chicu.aibot.strategy.service.CandleService;
import com.chicu.aibot.strategy.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class BollingerBandsStrategy implements TradingStrategy {

    private final BollingerStrategySettingsService settingsService;
    private final CandleService candleService;
    private final OrderService orderService;
    private final MarketLiveService liveService;

    /** Простая «позиция» по чатам: одна активная позиция на чат. */
    private final Map<Long, Position> positions = new ConcurrentHashMap<>();

    /* ================= TradingStrategy ================= */

    @Override
    public StrategyType getType() {
        return StrategyType.BOLLINGER_BANDS;
    }

    @Override
    public void start(Long chatId) {
        positions.remove(chatId);
        BollingerStrategySettings s = settingsService.getOrCreate(chatId);
        log.info("[BOLL] chatId={} started (symbol={}, tf={}, period={}, k={}, TP={}%, SL={}%, long={}, short={})",
                chatId, safeSymbol(s.getSymbol()), safeTf(s.getTimeframe()),
                nvl(s.getPeriod()), nvl(s.getStdDevMultiplier(), 2.0),
                nvl(s.getTakeProfitPct(), 1.0), nvl(s.getStopLossPct(), 0.5),
                Boolean.TRUE.equals(s.getAllowLong()), Boolean.TRUE.equals(s.getAllowShort()));
    }

    @Override
    public void stop(Long chatId) {
        positions.remove(chatId);
        log.info("[BOLL] chatId={} stopped; local state cleared", chatId);
    }

    @Override
    public void onPriceUpdate(Long chatId, double lastPrice) {
        final BollingerStrategySettings s = settingsService.getOrCreate(chatId);
        if (!s.isActive()) return;

        final String symbol = safeSymbol(s.getSymbol());
        final String tf     = safeTf(s.getTimeframe());
        final int    period = Math.max(5, nvl(s.getPeriod()));
        final double k      = Math.max(0.0, nvl(s.getStdDevMultiplier(), 2.0));
        final double tpFrac = Math.max(0.0, nvl(s.getTakeProfitPct(), 1.0)) / 100.0;
        final double slFrac = Math.max(0.0, nvl(s.getStopLossPct(), 0.5))   / 100.0;
        final double qty    = Math.max(0.0, nvl(s.getOrderVolume(), 0.0));

        if (qty <= 0.0) return; // нечем торговать

        // Берём последние period свечей (как в других стратегиях)
        List<Candle> candles = candleService.getCandles(chatId, symbol, tf, period);
        if (candles == null || candles.size() < period) return;

        // close может быть BigDecimal — конвертируем в double
        double[] closes = new double[candles.size()];
        for (int i = 0; i < candles.size(); i++) {
            BigDecimal c = candles.get(i).getClose();
            closes[i] = (c != null ? c.doubleValue() : lastPrice);
        }

        double sma   = mean(closes);
        double sigma = stddev(closes);
        double upper = sma + k * sigma;
        double lower = sma - k * sigma;

        Position pos = positions.get(chatId);

        // === ВХОД ===
        if (pos == null) {
            if (Boolean.TRUE.equals(s.getAllowLong()) && lastPrice <= lower) {
                placeMarketSafe(chatId, symbol, true, qty); // BUY
                positions.put(chatId, new Position(Side.LONG, lastPrice, qty));
                log.info("[BOLL] chatId={} LONG open @{} qty={}", chatId, fmt(lastPrice), fmtQty(qty));
                return;
            }
            if (Boolean.TRUE.equals(s.getAllowShort()) && lastPrice >= upper) {
                placeMarketSafe(chatId, symbol, false, qty); // SELL
                positions.put(chatId, new Position(Side.SHORT, lastPrice, qty));
                log.info("[BOLL] chatId={} SHORT open @{} qty={}", chatId, fmt(lastPrice), fmtQty(qty));
                return;
            }
            return;
        }

        // === ВЫХОД (TP/SL) ===
        switch (pos.side) {
            case LONG -> {
                boolean takeProfit = lastPrice >= pos.entry * (1.0 + tpFrac);
                boolean stopLoss   = (slFrac > 0) && lastPrice <= pos.entry * (1.0 - slFrac);
                if (takeProfit || stopLoss) {
                    placeMarketSafe(chatId, symbol, false, pos.qty); // SELL
                    positions.remove(chatId);
                    double pnlPct = (lastPrice - pos.entry) / pos.entry * 100.0;
                    log.info("[BOLL] chatId={} LONG close @{} PnL={}%", chatId, fmt(lastPrice), fmtPct(pnlPct));
                }
            }
            case SHORT -> {
                boolean takeProfit = lastPrice <= pos.entry * (1.0 - tpFrac);
                boolean stopLoss   = (slFrac > 0) && lastPrice >= pos.entry * (1.0 + slFrac);
                if (takeProfit || stopLoss) {
                    placeMarketSafe(chatId, symbol, true, pos.qty); // BUY
                    positions.remove(chatId);
                    double pnlPct = (pos.entry - lastPrice) / pos.entry * 100.0;
                    log.info("[BOLL] chatId={} SHORT close @{} PnL={}%", chatId, fmt(lastPrice), fmtPct(pnlPct));
                }
            }
        }
    }

    @Override
    public double getCurrentPrice(Long chatId) {
        BollingerStrategySettings s = settingsService.getOrCreate(chatId);
        LiveSnapshot snap = liveService.build(chatId, safeSymbol(s.getSymbol()));
        return snap.getLastPrice();
    }

    /* ================= helpers ================= */

    @Value
    private static class Position {
        Side   side;
        double entry;
        double qty;
    }
    private enum Side { LONG, SHORT }

    private static String safeSymbol(String s) { return (s == null || s.isBlank()) ? "BTCUSDT" : s; }
    private static String safeTf(String tf)    { return (tf == null || tf.isBlank()) ? "1m" : tf; }

    private static int    nvl(Integer v)   { return v != null ? v : 20; }
    private static double nvl(Double v, double def) { return v != null ? v : def; }

    private static String fmt(double v)    { return String.format("%,.8f", v); }
    private static String fmtQty(double v) { return String.format("%,.6f", v); }
    private static String fmtPct(double v) { return String.format("%.2f", v); }

    private void placeMarketSafe(Long chatId, String symbol, boolean buy, double qty) {
        try {
            Order.Side side = buy ? Order.Side.BUY : Order.Side.SELL; // <-- правильный enum
            orderService.placeMarket(chatId, symbol, side, qty);
        } catch (Exception e) {
            log.warn("[BOLL] placeMarket failed: chatId={}, symbol={}, side={}, qty={}, err={}",
                    chatId, symbol, (buy ? "BUY" : "SELL"), fmtQty(qty), e.toString());
        }
    }

    private static double mean(double[] a) {
        double s = 0.0;
        for (double v : a) s += v;
        return s / Math.max(1, a.length);
    }

    private static double stddev(double[] a) {
        int n = a.length;
        if (n < 2) return 0.0;
        double m = mean(a), ss = 0.0;
        for (double v : a) { double d = v - m; ss += d * d; }
        return Math.sqrt(ss / (n - 1));
    }
}
