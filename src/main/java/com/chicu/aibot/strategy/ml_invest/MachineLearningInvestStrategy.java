package com.chicu.aibot.strategy.ml_invest;

import com.chicu.aibot.strategy.StrategyType;
import com.chicu.aibot.strategy.TradingStrategy;
import com.chicu.aibot.strategy.ml_invest.model.MachineLearningInvestStrategySettings;
import com.chicu.aibot.strategy.ml_invest.model.MlInvestModelState;
import com.chicu.aibot.strategy.ml_invest.service.MachineLearningInvestStrategySettingsService;
import com.chicu.aibot.strategy.ml_invest.service.MlDataPipelineService;
import com.chicu.aibot.strategy.ml_invest.service.MlInvestModelStateService;
import com.chicu.aibot.strategy.model.Order;
import com.chicu.aibot.strategy.service.CandleService;
import com.chicu.aibot.strategy.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Реализация стратегии MachineLearningInvest с автотренингом и сохранением состояния в БД.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MachineLearningInvestStrategy implements TradingStrategy {

    private final MachineLearningInvestStrategySettingsService settingsService;
    private final MlDataPipelineService pipeline;
    private final MlInvestModelStateService modelStateService;
    private final OrderService orderService;
    private final CandleService candleService;

    @Value("${ml.invest.retrainIfOlderThanHours:12}")
    private int retrainIfOlderThanHours;

    @Value("${ml.invest.evaluateEverySeconds:60}")
    private int evaluateEverySeconds;

    @Value("${ml.invest.maxStubCyclesBeforeRetrain:3}")
    private int maxStubCyclesBeforeRetrain;

    private final ThreadPoolTaskScheduler scheduler = createScheduler();

    private final Map<Long, List<Order>> activeOrders = new ConcurrentHashMap<>();
    private final Map<Long, List<String>> universeByChat = new ConcurrentHashMap<>();
    private final Map<Long, String> modelRefByChat = new ConcurrentHashMap<>();
    private final Map<Long, ScheduledFuture<?>> jobs = new ConcurrentHashMap<>();
    private final Map<Long, Integer> stubCounters = new ConcurrentHashMap<>();

    /* ==================== TradingStrategy API ==================== */

    @Override
    public StrategyType getType() {
        return StrategyType.MACHINE_LEARNING_INVEST;
    }

    @Override
    public void start(Long chatId) {
        MachineLearningInvestStrategySettings s = settingsService.getOrCreate(chatId);
        MlInvestModelState state = modelStateService.getOrCreate(chatId);

        var universe = pipeline.pickUniverse(chatId, s.getTimeframe(), s.getUniverseSize(), s.getMin24hQuoteVolume());
        universeByChat.put(chatId, universe);

        // Загружаем существующую модель, если она готова
        if ("ready".equalsIgnoreCase(state.getStatus()) && state.getModelPath() != null) {
            modelRefByChat.put(chatId, state.getModelPath());
            log.info("[ML] Использую модель из БД: {}", state.getModelPath());
        } else {
            log.info("[ML] Модель не найдена или устарела — запускаю обучение...");
            retrainModel(chatId, s, universe);
        }

        activeOrders.computeIfAbsent(chatId, k -> new ArrayList<>());
        stubCounters.put(chatId, 0);

        cancelJob(chatId);
        int sec = Math.max(10, evaluateEverySeconds);
        var f = scheduler.scheduleAtFixedRate(() -> safeEval(chatId), Duration.ofSeconds(sec));
        jobs.put(chatId, f);

        log.info("[ML] start chatId={} universe(size={}) quota={} maxTrades={}",
                chatId, universe.size(), s.getQuota(), s.getMaxTradesPerQuota());
    }

    @Override
    public void stop(Long chatId) {
        cancelJob(chatId);
        activeOrders.remove(chatId);
        universeByChat.remove(chatId);
        modelRefByChat.remove(chatId);
        stubCounters.remove(chatId);
        log.info("[ML] stop chatId={}", chatId);
    }

    @Override
    public void onPriceUpdate(Long chatId, double price) {
        // стратегия ML работает по расписанию
    }

    @Override
    public double getCurrentPrice(Long chatId) {
        try {
            var uni = universeByChat.get(chatId);
            if (uni != null && !uni.isEmpty()) {
                var candles = candleService.getCandles(chatId, uni.getFirst(),
                        settingsService.getOrCreate(chatId).getTimeframe(), 1);
                if (candles != null && !candles.isEmpty() && candles.getFirst().getClose() != null) {
                    return candles.getFirst().getClose().doubleValue();
                }
            }
        } catch (Exception e) {
            log.warn("[ML] getCurrentPrice error: {}", e.getMessage());
        }
        return 0.0;
    }

    /* ==================== внутренняя логика ==================== */

    private void safeEval(Long chatId) {
        try {
            evaluateAll(chatId);
        } catch (Exception e) {
            log.warn("[ML] eval error: {}", e.getMessage());
        }
    }

    private void evaluateAll(Long chatId) {
        var s = settingsService.getOrCreate(chatId);
        var universe = universeByChat.getOrDefault(chatId, List.of());
        if (universe.isEmpty()) return;

        var modelRef = modelRefByChat.get(chatId);
        if (modelRef == null) return;

        var proba = pipeline.predict(chatId, modelRef, universe, s.getTimeframe());
        if (proba == null || proba.isEmpty()) {
            handleStubModel(chatId);
            return;
        }

        Object maybeStatus = proba.get("status");

        stubCounters.put(chatId, 0);

        // === основная логика торговли ===
        var ranked = proba.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .sorted((a, b) -> Double.compare(b.getValue().buy, a.getValue().buy))
                .toList();

        var tradables = ranked.stream()
                .limit(s.getTradeTopN())
                .map(Map.Entry::getKey)
                .toList();

        BigDecimal posSize = positionSize(s.getQuota(), s.getMaxTradesPerQuota());

        for (String sym : tradables) {
            var p = proba.get(sym);
            if (p == null) continue;
            boolean buy = p.buy > p.sell && p.buy > 0.55;
            boolean sell = p.sell > p.buy && p.sell > 0.55;
            boolean hasOpen = hasOpenPosition(chatId, sym);

            if (buy && !hasOpen && canOpenMore(chatId, s.getMaxTradesPerQuota())) {
                placeMarket(chatId, sym, Order.Side.BUY, posSize);
            } else if (sell && hasOpen) {
                placeMarket(chatId, sym, Order.Side.SELL, posSize);
                markClosed(chatId, sym);
            }
        }
    }

    /** обработка случая, когда модель не готова */
    private void handleStubModel(Long chatId) {
        int count = stubCounters.getOrDefault(chatId, 0) + 1;
        stubCounters.put(chatId, count);
        log.info("[ML] Модель не готова ({} из {}) — наблюдение (chatId={})",
                count, maxStubCyclesBeforeRetrain, chatId);

        if (count >= maxStubCyclesBeforeRetrain) {
            log.warn("[ML] Модель не обучена слишком долго — инициирую переобучение...");
            var s = settingsService.getOrCreate(chatId);
            var universe = universeByChat.getOrDefault(chatId, List.of());
            retrainModel(chatId, s, universe);
            stubCounters.put(chatId, 0);
        }
    }

    private void retrainModel(Long chatId, MachineLearningInvestStrategySettings s, List<String> universe) {
        try {
            var datasetPath = pipeline.buildTrainingDataset(chatId, universe, s.getTimeframe(), s.getTrainingWindowDays());
            String newModel = pipeline.trainIfNeeded(chatId, datasetPath, Duration.ofHours(retrainIfOlderThanHours), true);

            // сохраняем в БД состояние модели
            var state = MlInvestModelState.builder()
                    .chatId(chatId)
                    .modelPath(newModel)
                    .datasetPath(datasetPath.toString())
                    .timeframe(s.getTimeframe())
                    .trainingWindowDays(s.getTrainingWindowDays())
                    .universeSize(universe.size())
                    .lastTrainAt(LocalDateTime.now())
                    .accuracy(BigDecimal.ONE) // TODO: заменить реальным accuracy
                    .status("ready")
                    .version("1.0.0")
                    .build();

            modelStateService.saveState(state);
            modelRefByChat.put(chatId, newModel);

            log.info("[ML] ✅ Авто-переобучение завершено (chatId={}, model={})", chatId, newModel);
        } catch (Exception e) {
            log.error("[ML] ❌ Ошибка авто-переобучения: {}", e.getMessage(), e);
        }
    }

    private boolean isModelNotReady(String status) {
        return switch (status.toLowerCase()) {
            case "no_model", "stub_created", "empty_dataset",
                 "dataset_load_failed", "load_failed", "predict_failed" -> true;
            default -> false;
        };
    }

    /* ==================== helpers ==================== */

    private static ThreadPoolTaskScheduler createScheduler() {
        var ts = new ThreadPoolTaskScheduler();
        ts.setPoolSize(2);
        ts.setThreadNamePrefix("ml-invest-");
        ts.initialize();
        return ts;
    }

    private void cancelJob(Long chatId) {
        Optional.ofNullable(jobs.remove(chatId)).ifPresent(f -> f.cancel(false));
    }

    private BigDecimal positionSize(BigDecimal quota, Integer maxTrades) {
        if (quota == null) return BigDecimal.ZERO;
        if (maxTrades == null || maxTrades <= 0) return quota;
        return quota.divide(new BigDecimal(maxTrades), java.math.MathContext.DECIMAL64);
    }

    private boolean hasOpenPosition(Long chatId, String symbol) {
        return activeOrders.getOrDefault(chatId, List.of()).stream()
                .anyMatch(o -> symbol.equalsIgnoreCase(o.getSymbol()) && !o.isClosed());
    }

    private boolean canOpenMore(Long chatId, int maxTrades) {
        long open = activeOrders.getOrDefault(chatId, List.of()).stream()
                .filter(o -> !o.isClosed())
                .count();
        return open < maxTrades;
    }

    private void placeMarket(Long chatId, String symbol, Order.Side side, BigDecimal amountQuote) {
        try {
            var o = orderService.placeMarket(chatId, symbol, side, amountQuote.doubleValue());
            activeOrders.computeIfAbsent(chatId, k -> new ArrayList<>()).add(o);
            log.info("[ML] OPEN {} {} amount={}", side, symbol, amountQuote);
        } catch (Exception e) {
            log.warn("[ML] placeMarket failed {} {}: {}", symbol, side, e.getMessage());
        }
    }

    private void markClosed(Long chatId, String symbol) {
        var list = activeOrders.getOrDefault(chatId, new ArrayList<>());
        list.stream()
                .filter(o -> symbol.equalsIgnoreCase(o.getSymbol()) && !o.isClosed())
                .findFirst()
                .ifPresent(o -> o.setClosed(true));
    }
}
