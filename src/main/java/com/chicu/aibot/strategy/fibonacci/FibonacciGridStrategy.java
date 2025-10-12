package com.chicu.aibot.strategy.fibonacci;

import com.chicu.aibot.strategy.TradingStrategy;
import com.chicu.aibot.strategy.StrategyType;
import com.chicu.aibot.strategy.fibonacci.model.FibonacciGridStrategySettings;
import com.chicu.aibot.strategy.fibonacci.service.FibonacciGridStrategySettingsService;
import com.chicu.aibot.strategy.model.Candle;
import com.chicu.aibot.strategy.model.Order;
import com.chicu.aibot.strategy.service.CandleService;
import com.chicu.aibot.strategy.service.OrderService;
import com.chicu.aibot.strategy.service.OrderHousekeeperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FibonacciGridStrategy implements TradingStrategy {

    private final FibonacciGridStrategySettingsService settingsService;
    private final CandleService candleService;
    private final OrderService orderService;

    /** Уборщик ордеров: удаляет «мертвые», дубликаты и лишние пер-сторону */
    private final OrderHousekeeperService orderHousekeeperService;

    /** Активные ордера для каждого чата (in-memory кэш) */
    private final Map<Long, List<Order>> activeOrders = new HashMap<>();

    @Override
    public StrategyType getType() {
        return StrategyType.FIBONACCI_GRID;
    }

    @Override
    public void start(Long chatId) {
        // создаём кэш
        activeOrders.put(chatId, new ArrayList<>());

        // подтягиваем уже открытые ордера с биржи, чтобы не плодить дубликаты после рестарта
        try {
            FibonacciGridStrategySettings cfg = settingsService.getOrCreate(chatId);
            String symbol = nvl(cfg.getSymbol(), "ETHUSDT");
            List<Order> fromExchange = orderService.loadActiveOrders(chatId, symbol);
            if (fromExchange != null && !fromExchange.isEmpty()) {
                // в кэш только не отменённые/не закрытые
                List<Order> cleaned = fromExchange.stream()
                        .filter(o -> !o.isCancelled() && !o.isClosed())
                        .collect(Collectors.toCollection(ArrayList::new));
                activeOrders.put(chatId, cleaned);
                log.info("FIBONACCI_GRID стартовал для chatId={}, подхвачено активных ордеров: {}", chatId, cleaned.size());
            } else {
                log.info("FIBONACCI_GRID стартовал для chatId={}, активных ордеров нет", chatId);
            }
        } catch (Throwable t) {
            log.warn("Старт стратегии: не удалось загрузить активные ордера: {}", t.getMessage());
        }
    }

    @Override
    public void stop(Long chatId) {
        List<Order> orders = activeOrders.remove(chatId);
        if (orders != null) {
            for (Order o : orders) {
                // отменяем только то, что реально открыто и не закрыто
                if (!o.isCancelled() && !o.isClosed() && !o.isFilled()) {
                    try {
                        orderService.cancel(chatId, o);
                    } catch (Exception ex) {
                        log.debug("Cancel failed (ignored): {}", ex.getMessage());
                    }
                }
            }
        }
        log.info("FIBONACCI_GRID остановлен для chatId={}", chatId);
    }

    @Override
    public void onPriceUpdate(Long chatId, double currentPrice) {
        FibonacciGridStrategySettings cfg = settingsService.getOrCreate(chatId);
        String symbol = nvl(cfg.getSymbol(), "ETHUSDT");

        // берём копию текущего кэша, работаем с ней (потом заменим атомарно)
        List<Order> cache = new ArrayList<>(activeOrders.computeIfAbsent(chatId, k -> new ArrayList<>()));

        // 0) Актуализируем статусы ордеров, чтобы уловить FILLED/EXPIRED/CANCELED и т.п.
        try {
            orderService.refreshOrderStatuses(chatId, symbol, cache);
        } catch (Throwable t) {
            log.debug("refreshOrderStatuses недоступен/упал: {}", t.getMessage());
        }

        // 1) Свечи
        List<Candle> candles = candleService.getCandles(
                chatId,
                symbol,
                nvl(cfg.getTimeframe(), "1m"),
                nvl(cfg.getCachedCandlesLimit(), 500)
        );

        if (candles == null || candles.isEmpty()) {
            log.warn("Свечи не получены: chatId={}, symbol={}, tf={}", chatId, symbol, cfg.getTimeframe());
            // подчистим локальный кэш от отменённых/закрытых и вернём
            cache.removeIf(o -> o.isCancelled() || o.isClosed());
            activeOrders.put(chatId, cache);
            return;
        }

        // 2) Мин/макс за окно
        double minPrice = candles.stream()
                .map(Candle::getLow)
                .filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .min()
                .orElse(currentPrice);

        double maxPrice = candles.stream()
                .map(Candle::getHigh)
                .filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .max()
                .orElse(currentPrice);

        double range = maxPrice - minPrice;
        if (range <= 0) {
            log.debug("Нулевой диапазон цен: min={} max={} chatId={}", minPrice, maxPrice, chatId);
            cache.removeIf(o -> o.isCancelled() || o.isClosed());
            activeOrders.put(chatId, cache);
            return;
        }

        // 3) Уровни Фибо
        List<Double> levels = cfg.getLevels() == null ? List.of(0.236, 0.382, 0.5, 0.618, 0.786) : cfg.getLevels();
        List<Double> fibPrices = new ArrayList<>(levels.size());
        for (Double level : levels) {
            double v = minPrice + level * range;
            fibPrices.add(v);
        }
        fibPrices.sort(null);

        boolean allowLong  = Boolean.TRUE.equals(cfg.getAllowLong());
        boolean allowShort = Boolean.TRUE.equals(cfg.getAllowShort());

        // 4) Ставим лимитки на уровнях, с ограничением maxActiveOrders и без дубликатов
        int maxActive = (cfg.getMaxActiveOrders() == null) ? Integer.MAX_VALUE : Math.max(0, cfg.getMaxActiveOrders());
        int openNow = (int) cache.stream()
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

            boolean already = cache.stream()
                    .filter(o -> !o.isCancelled() && !o.isClosed())
                    .anyMatch(o -> Math.abs(o.getPrice() - priceLevel) < PRICE_EPS
                            && o.getSide() == side);

            if (!already) {
                Order o = orderService.placeLimit(
                        chatId,
                        symbol,
                        side,
                        priceLevel,
                        nvl(cfg.getOrderVolume(), 0.1) // дефолт, если null
                );
                if (o != null) {
                    cache.add(o);
                    placed++;
                    log.info("Выставлен лимитный ордер: chatId={}, side={}, price={}, qty={}",
                            chatId, side, priceLevel, cfg.getOrderVolume());
                }
            }
        }

        // 5) TP/SL только по ПОЛНОСТЬЮ ИСПОЛНЕННЫМ ордерам (избегаем путаницы с частичными объёмами)
        List<Order> filledOrders = cache.stream()
                .filter(Order::isFilled)
                .toList();

        double totalVol = filledOrders.stream()
                .mapToDouble(Order::getVolume) // для FILLED это полный объём
                .sum();

        if (totalVol > 0) {
            double value = filledOrders.stream()
                    .mapToDouble(o -> o.getPrice() * o.getVolume())
                    .sum();
            double avgEntry = value / totalVol;

            double tp = avgEntry * (1 + nvl(cfg.getTakeProfitPct(), 0.6) / 100.0);
            double sl = avgEntry * (1 - nvl(cfg.getStopLossPct(), 0.8)   / 100.0);

            if (currentPrice >= tp || currentPrice <= sl) {
                // закрываем только FILLED (точно знаем полный объём)
                for (Order o : new ArrayList<>(filledOrders)) {
                    if (!o.isClosed() && !o.isCancelled()) {
                        try {
                            orderService.closePosition(chatId, o);
                        } catch (Exception ex) {
                            log.debug("closePosition failed (ignored): {}", ex.getMessage());
                        }
                    }
                }
                log.info("Достигнут TP/SL: avgEntry={}, tp={}, sl={}, current={}. Позиции закрыты.",
                        avgEntry, tp, sl, currentPrice);
            }
        }

        // 6) Обновляем кэш: выкидываем отменённые/закрытые
        cache.removeIf(o -> o.isCancelled() || o.isClosed());
        activeOrders.put(chatId, cache);

        // 7) HOUSEKEEPER: чистим дубли, «мертвые» и лишние (пер-сторону) на бирже и в БД
        try {
            int perSideLimit = (cfg.getMaxActiveOrders() == null || cfg.getMaxActiveOrders() <= 0)
                    ? Integer.MAX_VALUE
                    : cfg.getMaxActiveOrders();
            var hk = orderHousekeeperService.reconcile(chatId, symbol, perSideLimit);
            if (hk.getRemovedDb() > 0 || hk.getCancelled() > 0) {
                log.info("FIB HK[{}:{}]: removedDb={}, cancelled={}, left BUY={}, SELL={}",
                        chatId, symbol, hk.getRemovedDb(), hk.getCancelled(), hk.getBuyActive(), hk.getSellActive());
            }
        } catch (Throwable t) {
            log.warn("FIB HK failed [{}:{}]: {}", chatId, symbol, t.getMessage());
        }
    }

    @Override
    public double getCurrentPrice(Long chatId) {
        FibonacciGridStrategySettings cfg = settingsService.getOrCreate(chatId);
        String symbol = nvl(cfg.getSymbol(), "ETHUSDT");
        List<Candle> candles = candleService.getCandles(
                chatId,
                symbol,
                nvl(cfg.getTimeframe(), "1m"),
                1
        );
        if (candles == null || candles.isEmpty()) return 0.0;
        Candle last = candles.get(candles.size() - 1);
        return last.getClose() == null ? 0.0 : last.getClose().doubleValue();
    }

    /* ================= helpers ================= */

    private static String nvl(String s, String def) {
        return (s == null || s.isBlank()) ? def : s;
    }

    private static int nvl(Integer v, int def) {
        return v == null ? def : v;
    }

    private static double nvl(Double v, double def) {
        return v == null ? def : v;
    }
}
