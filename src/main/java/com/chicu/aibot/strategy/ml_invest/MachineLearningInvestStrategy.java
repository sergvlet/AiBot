package com.chicu.aibot.strategy.ml_invest;

import com.chicu.aibot.strategy.StrategyType;
import com.chicu.aibot.strategy.TradingStrategy;
import com.chicu.aibot.strategy.model.Order;
import com.chicu.aibot.strategy.ml_invest.model.MachineLearningInvestStrategySettings;
import com.chicu.aibot.strategy.ml_invest.service.MachineLearningInvestStrategySettingsService;
import com.chicu.aibot.strategy.service.CandleService;
import com.chicu.aibot.strategy.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MachineLearningInvestStrategy implements TradingStrategy {

    private final CandleService candleService;
    private final OrderService orderService;
    private final MachineLearningInvestStrategySettingsService settingsService;

    @Override
    public StrategyType getType() {
        return StrategyType.MACHINE_LEARNING_INVEST;
    }

    @Override
    public void start(Long chatId) {
        MachineLearningInvestStrategySettings s = settingsService.getOrCreate(chatId);

        // Быстрая валидация объёма, чтобы не гонять планировщик впустую
        BigDecimal dryQty = resolveOrderQty(s, BigDecimal.ONE);
        if (dryQty.signum() <= 0) {
            log.warn("⚠️ [ML-Invest] Старт отклонён: не задан объём (режим={}, qty={}, quote={})",
                    s.isUseQuoteAmount() ? "Quote" : "Qty", s.getOrderQty(), s.getOrderQuoteAmount());
            return;
        }

        if (s.isActive()) {
            log.info("ℹ️ [ML-Invest] Уже запущена: chatId={} symbol={} tf={}", chatId, s.getSymbol(), s.getTimeframe());
            return;
        }
        s.setActive(true);
        settingsService.save(s);
        log.info("✅ [ML-Invest] Старт: chatId={} symbol={} tf={}", chatId, s.getSymbol(), s.getTimeframe());
    }

    @Override
    public void stop(Long chatId) {
        MachineLearningInvestStrategySettings s = settingsService.getOrCreate(chatId);
        if (!s.isActive()) {
            log.info("ℹ️ [ML-Invest] Уже остановлена: chatId={}", chatId);
            return;
        }
        s.setActive(false);
        settingsService.save(s);
        log.info("🛑 [ML-Invest] Стоп: chatId={}", chatId);
    }

    @Override
    public void onPriceUpdate(Long chatId, double price) {
        MachineLearningInvestStrategySettings s = settingsService.getOrCreate(chatId);
        if (s == null || !s.isActive()) return;

        var candles = candleService.getCandles(chatId, s.getSymbol(), s.getTimeframe(), s.getCachedCandlesLimit());
        if (candles == null || candles.isEmpty()) {
            log.warn("⚠️ [ML-Invest] Пустые свечи: symbol={} tf={}", s.getSymbol(), s.getTimeframe());
            return;
        }

        List<BigDecimal> closes = new ArrayList<>(candles.size());
        for (Object c : candles) {
            BigDecimal close = extractClose(c);
            if (close != null) closes.add(close);
        }
        if (closes.size() < 2) return;
        double[] features = computeFeatures(closes);

        double pUp = callMlModel(s.getModelPath(), features);
        double pDown = 1.0 - pUp;

        double buyThr  = s.getBuyThreshold()  != null ? s.getBuyThreshold().doubleValue()  : 2.0;
        double sellThr = s.getSellThreshold() != null ? s.getSellThreshold().doubleValue() : 2.0;

        boolean buySignal  = Double.compare(pUp,   buyThr)  > 0;
        boolean sellSignal = Double.compare(pDown, sellThr) > 0;

        log.info("🤖 [ML-Invest] chatId={} {} pUp={} pDown={} buyThr={} sellThr={}, useQuoteAmount={}",
                chatId, s.getSymbol(), round4(pUp), round4(pDown),
                s.getBuyThreshold(), s.getSellThreshold(), s.isUseQuoteAmount());

        BigDecimal qty = resolveOrderQty(s, BigDecimal.valueOf(price));
        if (qty == null || qty.signum() <= 0) {
            log.warn("⚠️ [ML-Invest] Пропуск: некорректный объём сделки (qty={})", qty);
            return;
        }

        try {
            if (buySignal && !sellSignal) {
                orderService.placeMarket(chatId, s.getSymbol(), Order.Side.BUY, qty.doubleValue());
                log.info("✅ [ML-Invest] BUY {} qty={}", s.getSymbol(), qty);
            } else if (sellSignal && !buySignal) {
                orderService.placeMarket(chatId, s.getSymbol(), Order.Side.SELL, qty.doubleValue());
                log.info("✅ [ML-Invest] SELL {} qty={}", s.getSymbol(), qty);
            } else {
                log.debug("🟰 [ML-Invest] HOLD {}", s.getSymbol());
            }
        } catch (Exception ex) {
            log.error("❌ [ML-Invest] Ошибка выставления ордера: {}", ex.getMessage(), ex);
        }
    }

    @Override
    public double getCurrentPrice(Long chatId) {
        MachineLearningInvestStrategySettings s = settingsService.getOrCreate(chatId);
        try {
            var candles = candleService.getCandles(chatId, s.getSymbol(), s.getTimeframe(), 1);
            if (candles == null || candles.isEmpty()) return 0.0;
            BigDecimal close = extractClose(candles.get(candles.size() - 1));
            return close != null ? close.doubleValue() : 0.0;
        } catch (Exception e) {
            log.error("❌ [ML-Invest] getCurrentPrice error: {}", e.getMessage(), e);
            return 0.0;
        }
    }

    /* ================= helpers ================= */

    private static BigDecimal round4(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal extractClose(Object candle) {
        if (candle == null) return null;
        try {
            Method m = candle.getClass().getMethod("getClose");
            Object val = m.invoke(candle);
            if (val instanceof BigDecimal bd) return bd;
            if (val instanceof Number n)     return BigDecimal.valueOf(n.doubleValue());
            return (val != null) ? new BigDecimal(val.toString()) : null;
        } catch (Exception e) {
            log.error("❌ [ML-Invest] Не удалось прочитать close у {}: {}", candle.getClass().getName(), e.getMessage());
            return null;
        }
    }

    private double[] computeFeatures(List<BigDecimal> closes) {
        int n = closes.size();
        double[] arr = new double[Math.max(0, n - 1)];
        for (int i = 1; i < n; i++) {
            BigDecimal prev = closes.get(i - 1);
            BigDecimal curr = closes.get(i);
            if (prev == null || prev.signum() == 0) {
                arr[i - 1] = 0.0;
            } else {
                arr[i - 1] = curr.subtract(prev)
                        .divide(prev, 8, RoundingMode.HALF_UP)
                        .doubleValue();
            }
        }
        return arr;
    }

    private double callMlModel(String modelPath, double[] features) {
        // TODO: заменить на PythonInferenceService/REST-инференс
        return Math.random();
    }

    /**
     * Расчёт объёма с fallback:
     *  - если режим Qty: берём orderQty, иначе fallback к Quote;
     *  - если режим Quote: qty = quote/price, иначе fallback к Qty.
     */
    private BigDecimal resolveOrderQty(MachineLearningInvestStrategySettings s, BigDecimal lastPrice) {
        boolean wantQuote = s.isUseQuoteAmount();
        BigDecimal qty;

        if (!wantQuote) {
            qty = (s.getOrderQty() != null && s.getOrderQty().signum() > 0) ? s.getOrderQty() : quoteToQty(s.getOrderQuoteAmount(), lastPrice);
        } else {
            qty = quoteToQty(s.getOrderQuoteAmount(), lastPrice);
            if (qty.signum() <= 0 && s.getOrderQty() != null && s.getOrderQty().signum() > 0) {
                qty = s.getOrderQty();
            }
        }
        return (qty != null && qty.signum() > 0) ? qty : BigDecimal.ZERO;
    }

    private BigDecimal quoteToQty(BigDecimal quote, BigDecimal lastPrice) {
        if (quote == null || quote.signum() <= 0 || lastPrice == null || lastPrice.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return quote.divide(lastPrice, 8, RoundingMode.DOWN);
    }
}
