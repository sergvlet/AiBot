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
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class FibonacciGridStrategy implements TradingStrategy {

    private final FibonacciGridStrategySettingsService settingsService;
    private final CandleService candleService;
    private final OrderService orderService;

    /** Активные ордера для каждого чата */
    private final Map<Long, List<Order>> activeOrders = new HashMap<>();

    @Override
    public StrategyType getType() {
        return StrategyType.FIBONACCI_GRID;
    }

    @Override
    public void start(Long chatId) {
        activeOrders.put(chatId, new ArrayList<>());
    }

    @Override
    public void stop(Long chatId) {
        List<Order> orders = activeOrders.remove(chatId);
        if (orders != null) {
            for (Order o : orders) {
                orderService.cancel(chatId, o);
            }
        }
    }

    @Override
    public void onPriceUpdate(Long chatId, double currentPrice) {
        FibonacciGridStrategySettings cfg = settingsService.getOrCreate(chatId);

        // 1) Берём последние свечи
        List<Candle> candles = candleService.getLastCandles(
                cfg.getSymbol(),
                cfg.getTimeframe(),
                cfg.getCachedCandlesLimit()
        );

        if (candles.isEmpty()) {
            return;
        }

        // 2) Находим минимум и максимум
        double minPrice = candles.stream()
                .mapToDouble(Candle::getLow)
                .min().orElse(currentPrice);
        double maxPrice = candles.stream()
                .mapToDouble(Candle::getHigh)
                .max().orElse(currentPrice);
        double range = maxPrice - minPrice;

        // 3) Строим ценовые уровни Фибоначчи
        List<Double> fibPrices = cfg.getLevels().stream()
                .map(level -> minPrice + level * range)
                .sorted()
                .toList();

        // 4) Выставляем лимитные ордера на каждый уровень, если ещё не выставлены
        List<Order> orders = activeOrders.computeIfAbsent(chatId, k -> new ArrayList<>());

        for (Double priceLevel : fibPrices) {
            boolean already = orders.stream()
                    .anyMatch(o -> Math.abs(o.getPrice() - priceLevel) < 1e-8);
            if (!already) {
                Order.Side side = priceLevel < currentPrice
                        ? Order.Side.BUY
                        : Order.Side.SELL;
                Order o = orderService.placeLimit(
                        chatId,
                        cfg.getSymbol(),
                        side,
                        priceLevel,
                        cfg.getOrderVolume()
                );
                orders.add(o);
            }
        }

        // 5) Рассчитываем среднюю цену исполненных ордеров и проверяем TP/SL
        double avgEntry = orders.stream()
                .filter(Order::isFilled)
                .mapToDouble(Order::getPrice)
                .average()
                .orElse(Double.NaN);

        if (!Double.isNaN(avgEntry)) {
            double tp = avgEntry * (1 + cfg.getTakeProfitPct() / 100.0);
            double sl = avgEntry * (1 - cfg.getStopLossPct()   / 100.0);
            if (currentPrice >= tp || currentPrice <= sl) {
                // закрываем все исполненные
                for (Order o : orders.stream().filter(Order::isFilled).toList()) {
                    orderService.closePosition(chatId, o);
                }
                orders.clear();
            }
        }

        // 6) Убираем из списка отменённые и закрытые
        orders.removeIf(o -> o.isCancelled() || o.isClosed());
    }

    @Override
    public double getCurrentPrice(Long chatId) {
        FibonacciGridStrategySettings cfg = settingsService.getOrCreate(chatId);
        List<Candle> candles = candleService.getLastCandles(
                cfg.getSymbol(),
                cfg.getTimeframe(),
                1
        );
        return candles.isEmpty()
                ? 0.0
                : candles.getLast().getClose();
    }
}
