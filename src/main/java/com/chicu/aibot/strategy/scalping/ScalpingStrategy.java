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
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
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
    }

    @Override
    public void stop(Long chatId) {
        List<Order> orders = activeOrders.remove(chatId);
        if (orders != null) {
            for (Order order : orders) {
                orderService.cancel(chatId, order);
            }
        }
    }

    @Override
    public void onPriceUpdate(Long chatId, double currentPrice) {
        ScalpingStrategySettings cfg = settingsService.getOrCreate(chatId);

        List<Candle> candles = candleService.getLastCandles(
            cfg.getSymbol(),
            cfg.getTimeframe(),
            cfg.getCachedCandlesLimit()
        );
        if (candles.size() < cfg.getWindowSize()) {
            return;
        }

        double open  = candles.get(candles.size() - cfg.getWindowSize()).getOpen();
        double close = candles.getLast().getClose();
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
        }

        orders.removeIf(Order::isClosed);
    }

    @Override
    public double getCurrentPrice(Long chatId) {
        ScalpingStrategySettings cfg = settingsService.getOrCreate(chatId);
        List<Candle> candles = candleService.getLastCandles(
            cfg.getSymbol(),
            cfg.getTimeframe(),
            1
        );
        return candles.isEmpty() ? 0.0 : candles.getFirst().getClose();
    }
}
