package com.chicu.aibot.strategy.scalping;

import com.chicu.aibot.strategy.StrategyType;
import com.chicu.aibot.strategy.TradingStrategy;
import com.chicu.aibot.strategy.model.Candle;
import com.chicu.aibot.strategy.model.Order;
import com.chicu.aibot.strategy.scalping.model.ScalpingStrategySettings;
import com.chicu.aibot.strategy.scalping.service.ScalpingStrategySettingsService;
import com.chicu.aibot.strategy.service.CandleService;
import com.chicu.aibot.strategy.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScalpingStrategy implements TradingStrategy {

    private final ScalpingStrategySettingsService settingsService;
    private final CandleService candleService;
    private final OrderService orderService;

    private final Map<Long, List<Order>> activeOrders = new HashMap<>();

    @Override
    public StrategyType getType() {
        return StrategyType.SCALPING;
    }

    @Override
    public void start(Long chatId) {
        activeOrders.put(chatId, new ArrayList<>());
        log.info("SCALPING стартовал для chatId={}", chatId);
    }

    @Override
    public void stop(Long chatId) {
        List<Order> orders = activeOrders.remove(chatId);
        if (orders != null) {
            for (Order order : orders) {
                orderService.cancel(chatId, order);
            }
        }
        log.info("SCALPING остановлен для chatId={}", chatId);
    }

    @Override
    public void onPriceUpdate(Long chatId, double currentPrice) {
        ScalpingStrategySettings cfg = settingsService.getOrCreate(chatId);

        // Берём последние свечи — НОВАЯ сигнатура с chatId
        List<Candle> candles = candleService.getCandles(
                chatId,
                cfg.getSymbol(),
                cfg.getTimeframe(),
                cfg.getCachedCandlesLimit()
        );
        if (candles.size() < cfg.getWindowSize()) {
            log.debug("Недостаточно свечей: chatId={}, need={}, have={}",
                    chatId, cfg.getWindowSize(), candles.size());
            return;
        }

        // Свечи возвращают BigDecimal — приводим к double
        int openIdx = Math.max(0, candles.size() - cfg.getWindowSize());
        double open  = candles.get(openIdx).getOpen().doubleValue();
        double close = candles.getLast().getClose().doubleValue();
        if (open <= 0) {
            log.warn("open<=0 для chatId={}, symbol={}, tf={}", chatId, cfg.getSymbol(), cfg.getTimeframe());
            return;
        }
        double changePct = ((close - open) / open) * 100.0;

        List<Order> orders = activeOrders.computeIfAbsent(chatId, k -> new ArrayList<>());

        if (Math.abs(changePct) >= cfg.getPriceChangeThreshold()) {
            Order.Side side = changePct > 0 ? Order.Side.BUY : Order.Side.SELL;
            Order order = orderService.placeMarket(
                    chatId,
                    cfg.getSymbol(),
                    side,
                    cfg.getOrderVolume()
            );
            orders.add(order);
            log.info("Сработал сигнал: changePct={}%, side={}, выставлен рыночный ордер qty={}",
                    String.format("%.4f", changePct), side, cfg.getOrderVolume());
        }

        // Чистим закрытые/отменённые
        orders.removeIf(o -> o.isClosed() || o.isCancelled());
    }

    @Override
    public double getCurrentPrice(Long chatId) {
        ScalpingStrategySettings cfg = settingsService.getOrCreate(chatId);
        List<Candle> candles = candleService.getCandles(
                chatId,
                cfg.getSymbol(),
                cfg.getTimeframe(),
                1
        );
        return candles.isEmpty()
                ? 0.0
                : candles.getLast().getClose().doubleValue();
    }
}
