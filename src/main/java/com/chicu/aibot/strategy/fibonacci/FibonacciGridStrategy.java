package com.chicu.aibot.strategy.fibonacci;

import com.chicu.aibot.strategy.TradingStrategy;
import com.chicu.aibot.strategy.StrategyType;
import com.chicu.aibot.strategy.fibonacci.model.FibonacciGridStrategySettings;
import com.chicu.aibot.strategy.fibonacci.service.FibonacciGridStrategySettingsService;
import com.chicu.aibot.strategy.model.Candle;
import com.chicu.aibot.strategy.model.Order;
import com.chicu.aibot.strategy.service.CandleService;
import com.chicu.aibot.strategy.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class FibonacciGridStrategy implements TradingStrategy {

    private final FibonacciGridStrategySettingsService settingsService;
    private final CandleService candleService;
    private final OrderService orderService;

    /** Активные ордера для каждого чата (in-memory кэш) */
    private final Map<Long, List<Order>> activeOrders = new HashMap<>();

    @Override
    public StrategyType getType() {
        return StrategyType.FIBONACCI_GRID;
    }

    @Override
    public void start(Long chatId) {
        activeOrders.put(chatId, new ArrayList<>());
        log.info("FIBONACCI_GRID стартовал для chatId={}", chatId);
    }

    @Override
    public void stop(Long chatId) {
        List<Order> orders = activeOrders.remove(chatId);
        if (orders != null) {
            for (Order o : orders) {
                orderService.cancel(chatId, o);
            }
        }
        log.info("FIBONACCI_GRID остановлен для chatId={}", chatId);
    }

    @Override
    public void onPriceUpdate(Long chatId, double currentPrice) {
        FibonacciGridStrategySettings cfg = settingsService.getOrCreate(chatId);

        // 0) Актуализируем статусы ордеров с биржи/БД, чтобы:
        //    - подхватить PARTIALLY_FILLED/FILLED
        //    - не дублировать лимитки после рестартов
        List<Order> orders = activeOrders.computeIfAbsent(chatId, k -> new ArrayList<>());
        try {
            orderService.refreshOrderStatuses(chatId, cfg.getSymbol(), orders);
        } catch (Throwable t) {
            // не валим стратегию, если метод ещё не внедрён
            log.debug("refreshOrderStatuses недоступен/упал: {}", t.getMessage());
        }

        // 1) Берём последние свечи
        List<Candle> candles = candleService.getCandles(
                chatId,
                cfg.getSymbol(),
                cfg.getTimeframe(),
                cfg.getCachedCandlesLimit()
        );

        if (candles.isEmpty()) {
            log.warn("Свечи не получены: chatId={}, symbol={}, tf={}", chatId, cfg.getSymbol(), cfg.getTimeframe());
            return;
        }

        // 2) Минимум/максимум по свечам
        double minPrice = candles.stream()
                .map(Candle::getLow)
                .mapToDouble(BigDecimal::doubleValue)
                .min()
                .orElse(currentPrice);

        double maxPrice = candles.stream()
                .map(Candle::getHigh)
                .mapToDouble(BigDecimal::doubleValue)
                .max()
                .orElse(currentPrice);

        double range = maxPrice - minPrice;
        if (range <= 0) {
            log.debug("Нулевой диапазон цен: min={} max={} chatId={}", minPrice, maxPrice, chatId);
            return;
        }

        // 3) Уровни Фибо
        List<Double> fibPrices = new ArrayList<>();
        for (Double level : cfg.getLevels()) {
            Double v = minPrice + level * range;
            fibPrices.add(v);
        }
        fibPrices.sort(null);

        boolean allowLong  = Boolean.TRUE.equals(cfg.getAllowLong());
        boolean allowShort = Boolean.TRUE.equals(cfg.getAllowShort());

        // 4) Лимитки на уровнях — ставим только если ещё нет активной (не отменённой/не закрытой) на этой цене.
        //    Также придерживаемся лимита maxActiveOrders (если задан).
        int maxActive = (cfg.getMaxActiveOrders() == null) ? Integer.MAX_VALUE : Math.max(0, cfg.getMaxActiveOrders());
        int openNow = (int) orders.stream()
                .filter(o -> !o.isCancelled() && !o.isClosed() && !o.isFilled())
                .count();
        int canPlace = Math.max(0, maxActive - openNow);
        int placed = 0;

        final double PRICE_EPS = 1e-8;

        for (Double priceLevel : fibPrices) {
            if (placed >= canPlace) break;

            Order.Side side = (priceLevel < currentPrice) ? Order.Side.BUY : Order.Side.SELL;
            if (side == Order.Side.BUY && !allowLong)  continue;
            if (side == Order.Side.SELL && !allowShort) continue;

            boolean already = orders.stream()
                    .filter(o -> !o.isCancelled() && !o.isClosed())
                    .anyMatch(o -> Math.abs(o.getPrice() - priceLevel) < PRICE_EPS
                                   && o.getSide() == side);

            if (!already) {
                Order o = orderService.placeLimit(
                        chatId,
                        cfg.getSymbol(),
                        side,
                        priceLevel,
                        cfg.getOrderVolume()
                );
                orders.add(o);
                placed++;
                log.info("Выставлен лимитный ордер: chatId={}, side={}, price={}, qty={}",
                        chatId, side, priceLevel, cfg.getOrderVolume());
            }
        }

        // 5) Взвешенная средняя цена входа по реально исполненному объёму
        double totalVol = orders.stream()
                .filter(o -> o.getVolume() > 0)   // учитываем частично/полностью исполненные
                .mapToDouble(Order::getVolume)
                .sum();

        if (totalVol > 0) {
            double value = orders.stream()
                    .filter(o -> o.getVolume() > 0)
                    .mapToDouble(o -> o.getPrice() * o.getVolume())
                    .sum();
            double avgEntry = value / totalVol;

            double tp = avgEntry * (1 + cfg.getTakeProfitPct() / 100.0);
            double sl = avgEntry * (1 - cfg.getStopLossPct()   / 100.0);

            if (currentPrice >= tp || currentPrice <= sl) {
                // закрываем только то, где есть исполненный объём
                for (Order o : new ArrayList<>(orders)) {
                    if (o.getVolume() > 0 && !o.isClosed() && !o.isCancelled()) {
                        orderService.closePosition(chatId, o);
                    }
                }
                // подчистка
                orders.removeIf(o -> o.isClosed() || o.isCancelled());
                log.info("Достигнут TP/SL: avgEntry={}, tp={}, sl={}, current={}. Позиции закрыты.",
                        avgEntry, tp, sl, currentPrice);
            }
        }

        // 6) Убираем отменённые/закрытые из кэша
        orders.removeIf(o -> o.isCancelled() || o.isClosed());
    }

    @Override
    public double getCurrentPrice(Long chatId) {
        FibonacciGridStrategySettings cfg = settingsService.getOrCreate(chatId);
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
