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
    // Анти-рывок после рестарта
    private final Map<Long, Long> nextDecisionAt = new HashMap<>();

    @Override
    public StrategyType getType() {
        return StrategyType.SCALPING;
    }

    @Override
    public void start(Long chatId) {
        // Инициализируем кэш
        activeOrders.put(chatId, new ArrayList<>());
        log.info("SCALPING стартовал для chatId={}", chatId);

        // Гидратация: подтягиваем открытые ордера, чтобы не стрелять повторно после рестарта
        try {
            ScalpingStrategySettings cfg = settingsService.getOrCreate(chatId);
            List<Order> fromExchange = orderService.loadActiveOrders(chatId, cfg.getSymbol());
            if (fromExchange != null && !fromExchange.isEmpty()) {
                List<Order> cleaned = new ArrayList<>();
                for (Order o : fromExchange) {
                    if (!o.isCancelled() && !o.isClosed()) cleaned.add(o);
                }
                activeOrders.put(chatId, cleaned);
                log.info("SCALPING: подхвачено активных ордеров: {}", cleaned.size());
            }
        } catch (Throwable t) {
            log.debug("SCALPING start: гидратация не удалась: {}", t.getMessage());
        }

        // Мягкий старт: 15 сек без решений
        nextDecisionAt.put(chatId, System.currentTimeMillis() + 15_000);
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

        // Мягкий старт/кулдаун после рестарта
        Long ts = nextDecisionAt.get(chatId);
        if (ts != null && System.currentTimeMillis() < ts) {
            return;
        }

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

        // Вычисляем изменение за окно
        int w = cfg.getWindowSize();
        double open  = candles.get(candles.size() - w).getClose().doubleValue();
        double close = candles.get(candles.size() - 1).getClose().doubleValue();

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
