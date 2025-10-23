package com.chicu.aibot.strategy.ml_invest;

import com.chicu.aibot.strategy.StrategyType;
import com.chicu.aibot.strategy.TradingStrategy;
import com.chicu.aibot.strategy.ml_invest.model.MachineLearningInvestStrategySettings;
import com.chicu.aibot.strategy.ml_invest.model.MlInvestModelState;
import com.chicu.aibot.strategy.ml_invest.service.MachineLearningInvestStrategySettingsService;
import com.chicu.aibot.strategy.ml_invest.service.MlDataPipelineService;
import com.chicu.aibot.strategy.ml_invest.service.MlInvestModelStateService;
import com.chicu.aibot.strategy.model.Candle;
import com.chicu.aibot.strategy.model.Order;
import com.chicu.aibot.strategy.service.CandleService;
import com.chicu.aibot.strategy.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Полная реализация MachineLearningInvest:
 * - использует настройки из БД (включая selectedPairs)
 * - обучает/переобучает модель при необходимости
 * - сохраняет состояние в MlInvestModelState
 * - по расписанию делает оценку и выставляет ордера
 *
 * ВАЖНО: OrderService.placeMarket ожидает quantity, поэтому qty
 * считаем из quoteAmount / lastPrice.
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

    /** Собственный мини-планировщик — изолирован от остальной системы стратегий. */
    private final ThreadPoolTaskScheduler scheduler = createScheduler();

    /** Кэш активных ордеров per chatId (минимально необходимый для логики). */
    private final Map<Long, List<Order>> activeOrders = new ConcurrentHashMap<>();
    /** Универсум символов per chatId. */
    private final Map<Long, List<String>> universeByChat = new ConcurrentHashMap<>();
    /** Путь/идентификатор модели per chatId. */
    private final Map<Long, String> modelRefByChat = new ConcurrentHashMap<>();
    /** Плановые задания per chatId. */
    private final Map<Long, ScheduledFuture<?>> jobs = new ConcurrentHashMap<>();
    /** Счётчик «пустых» прогнозов per chatId. */
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

        // 1) Универсум: выбранные пользователем или авто-подбор
        List<String> universe;
        if (s.getSelectedPairs() != null && !s.getSelectedPairs().isEmpty()) {
            universe = new ArrayList<>(s.getSelectedPairs());
            log.info("[ML] Используем пары из Telegram-настроек chatId={} → {}", chatId, universe);
        } else {
            universe = pipeline.pickUniverse(chatId, tf(s), universeSize(s), min24hQuoteVolume(s));
            log.info("[ML] Пары не выбраны — авто-универсум из {} пар", universe.size());
        }
        if (universe.isEmpty()) {
            log.warn("[ML] Универсум пуст — старт отклонён");
            return;
        }
        universeByChat.put(chatId, universe);

        // 2) Проверяем/обновляем модель
        String modelRef = state.getModelPath();
        boolean needRetrain = false;
        if (isBlank(modelRef)) {
            needRetrain = true;
            log.info("[ML] В БД нет пути к модели — требуется обучение");
        } else if (Boolean.TRUE.equals(s.getAutoRetrainOnStart())) {
            LocalDateTime lastTrainAt = state.getLastTrainAt();
            if (lastTrainAt == null || lastTrainAt.isBefore(LocalDateTime.now().minusHours(Math.max(1, retrainIfOlderThanHours)))) {
                needRetrain = true;
                log.info("[ML] Модель устарела (lastTrainAt={}) — запускаем переобучение", lastTrainAt);
            }
        }

        if (needRetrain) {
            retrainModel(chatId, s, universe);
            modelRef = modelRefByChat.get(chatId); // после retrain сохраняется сюда
        } else {
            modelRefByChat.put(chatId, modelRef);
            log.info("[ML] Используется модель из БД: {}", modelRef);
        }

        // 3) Планируем периодическую оценку
        activeOrders.computeIfAbsent(chatId, k -> new ArrayList<>());
        stubCounters.put(chatId, 0);
        cancelJob(chatId);
        int sec = Math.max(10, evaluateEverySeconds);
        ScheduledFuture<?> f = scheduler.scheduleAtFixedRate(() -> safeEval(chatId), Duration.ofSeconds(sec));
        jobs.put(chatId, f);

        log.info("[ML] ✅ Стратегия запущена (chatId={}, pairs={}, quota={}, maxTrades={})",
                chatId, universe.size(), quota(s), maxTrades(s));
    }

    @Override
    public void stop(Long chatId) {
        cancelJob(chatId);
        activeOrders.remove(chatId);
        universeByChat.remove(chatId);
        modelRefByChat.remove(chatId);
        stubCounters.remove(chatId);
        log.info("[ML] ⛔ Стратегия остановлена chatId={}", chatId);
    }

    @Override
    public void onPriceUpdate(Long chatId, double price) {
        // стратегия ML работает по расписанию, прямые обновления цены не требуются
    }

    @Override
    public double getCurrentPrice(Long chatId) {
        try {
            List<String> uni = universeByChat.get(chatId);
            if (uni != null && !uni.isEmpty()) {
                BigDecimal last = lastClose(chatId, uni.get(0), tf(settingsService.getOrCreate(chatId)));
                return last == null ? 0.0 : last.doubleValue();
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
        if (isBlank(modelRef)) {
            handleStubModel(chatId);
            return;
        }

        var proba = pipeline.predict(chatId, modelRef, universe, tf(s));
        if (proba == null || proba.isEmpty()) {
            handleStubModel(chatId);
            return;
        }

        stubCounters.put(chatId, 0);

        var ranked = proba.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .sorted((a, b) -> Double.compare(b.getValue().buy, a.getValue().buy))
                .toList();

        var tradables = ranked.stream()
                .limit(tradeTopN(s))
                .map(Map.Entry::getKey)
                .toList();

        BigDecimal perTradeQuote = positionSize(quota(s), maxTrades(s));

        for (String sym : tradables) {
            var p = proba.get(sym);
            if (p == null) continue;

            boolean buy  = p.buy  > p.sell && p.buy  > buyThreshold(s).doubleValue();
            boolean sell = p.sell > p.buy  && p.sell > sellThreshold(s).doubleValue();

            boolean hasOpen = hasOpenPosition(chatId, sym);

            if (buy && !hasOpen && canOpenMore(chatId, maxTrades(s))) {
                placeMarketByQuote(chatId, sym, Order.Side.BUY, perTradeQuote, tf(s));
            } else if (sell && hasOpen) {
                // простая логика выхода — MARKET SELL тем же размером (можно доработать под фактическую позицию)
                placeMarketByQuote(chatId, sym, Order.Side.SELL, perTradeQuote, tf(s));
                markClosed(chatId, sym);
            }
        }
    }

    /** Обработка ситуации, когда модель не даёт прогноз (stub/no_model/и т.п.) */
    private void handleStubModel(Long chatId) {
        int count = stubCounters.getOrDefault(chatId, 0) + 1;
        stubCounters.put(chatId, count);
        log.info("[ML] Модель не готова ({} / {}) — наблюдение", count, maxStubCyclesBeforeRetrain);

        if (count >= maxStubCyclesBeforeRetrain) {
            log.warn("[ML] Порог ожидания превышен — переобучение модели...");
            var s = settingsService.getOrCreate(chatId);
            retrainModel(chatId, s, universeByChat.getOrDefault(chatId, List.of()));
            stubCounters.put(chatId, 0);
        }
    }

    private void retrainModel(Long chatId, MachineLearningInvestStrategySettings s, List<String> universe) {
        if (universe == null || universe.isEmpty()) {
            log.warn("[ML] retrainModel: пустой универсум — пропуск");
            return;
        }
        try {
            var ds = pipeline.buildTrainingDataset(chatId, universe, tf(s), windowDays(s));
            String modelPath = pipeline.trainIfNeeded(
                    chatId,
                    ds,
                    Duration.ofHours(Math.max(1, retrainIfOlderThanHours)),
                    true // force
            );

            // сохраняем состояние модели в БД
            MlInvestModelState state = modelStateService.getOrCreate(chatId);
            state.setModelPath(modelPath);
            state.setDatasetPath(ds.toString());
            state.setTimeframe(tf(s));
            state.setTrainingWindowDays(windowDays(s));
            state.setUniverseSize(universe.size());
            state.setLastTrainAt(LocalDateTime.now());
            // при желании подтягивай метрики из Python — здесь placeholder:
            state.setAccuracy(BigDecimal.ONE);
            state.setStatus("ready");
            state.setVersion("1.0.0");
            modelStateService.saveState(state);

            modelRefByChat.put(chatId, modelPath);
            log.info("[ML] ✅ Модель обучена и сохранена (chatId={}, path={})", chatId, modelPath);
        } catch (Exception e) {
            log.error("[ML] ❌ Ошибка переобучения: {}", e.getMessage(), e);
        }
    }

    /* ==================== helpers ==================== */

    private static ThreadPoolTaskScheduler createScheduler() {
        ThreadPoolTaskScheduler ts = new ThreadPoolTaskScheduler();
        ts.setPoolSize(2);
        ts.setThreadNamePrefix("ml-invest-");
        ts.initialize();
        return ts;
    }

    private void cancelJob(Long chatId) {
        Optional.ofNullable(jobs.remove(chatId)).ifPresent(f -> f.cancel(false));
    }

    /** Делит квоту на число одновременно открываемых сделок. */
    private BigDecimal positionSize(BigDecimal quota, Integer maxTrades) {
        if (quota == null || quota.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        int mt = (maxTrades == null || maxTrades <= 0) ? 1 : maxTrades;
        return quota.divide(BigDecimal.valueOf(mt), 8, RoundingMode.DOWN);
    }

    private boolean hasOpenPosition(Long chatId, String symbol) {
        return activeOrders.getOrDefault(chatId, List.of()).stream()
                .anyMatch(o -> symbol.equalsIgnoreCase(o.getSymbol()));
    }

    private boolean canOpenMore(Long chatId, int maxTrades) {
        long open = activeOrders.getOrDefault(chatId, List.of()).size();
        return open < Math.max(1, maxTrades);
    }

    /** Выставление MARKET по сумме в quote — конвертируем в qty по последней цене. */
    private void placeMarketByQuote(Long chatId, String symbol, Order.Side side, BigDecimal quoteAmount, String timeframe) {
        BigDecimal last = lastClose(chatId, symbol, timeframe);
        if (last == null || last.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("[ML] Нет цены для {}, пропускаю MARKET {}", symbol, side);
            return;
        }
        BigDecimal qty = quoteAmount.divide(last, 8, RoundingMode.DOWN);
        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("[ML] Qty=0 для {} при quote={}, price={}", symbol, quoteAmount, last);
            return;
        }
        try {
            Order order = orderService.placeMarket(chatId, symbol, side, qty.doubleValue());
            if (side == Order.Side.BUY) {
                activeOrders.computeIfAbsent(chatId, k -> new ArrayList<>()).add(order);
            } else {
                // SELL — удаляем первую подходящую покупку из кэша (минимально необходимая логика)
                removeFirstBySymbol(activeOrders.getOrDefault(chatId, new ArrayList<>()), symbol);
            }
            log.info("[ML] OPEN {} {} qty={} (~{} quote) @ ~{}",
                    side, symbol, qty.stripTrailingZeros(), quoteAmount.stripTrailingZeros(), last.stripTrailingZeros());
        } catch (Exception e) {
            log.warn("[ML] placeMarket failed {} {}: {}", symbol, side, e.getMessage());
        }
    }

    private void removeFirstBySymbol(List<Order> list, String symbol) {
        if (list == null) return;
        for (Iterator<Order> it = list.iterator(); it.hasNext(); ) {
            Order o = it.next();
            if (symbol.equalsIgnoreCase(o.getSymbol())) {
                it.remove();
                return;
            }
        }
    }

    private void markClosed(Long chatId, String symbol) {
        // текущая реализация — через удаление из кэша при SELL (см. выше).
        // Если у Order есть setClosed(), можно пометить флагом.
    }

    private BigDecimal lastClose(Long chatId, String symbol, String timeframe) {
        try {
            List<Candle> candles = candleService.getCandles(chatId, symbol, timeframe, 1);
            if (candles == null || candles.isEmpty() || candles.get(0).getClose() == null) return BigDecimal.ZERO;
            return candles.get(0).getClose();
        } catch (Exception e) {
            log.warn("[ML] Ошибка получения цены {}: {}", symbol, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /* ====== безопасные геттеры настроек с дефолтами (во избежание NPE и несовпадений полей) ====== */

    private String tf(MachineLearningInvestStrategySettings s) {
        return (s.getTimeframe() == null || s.getTimeframe().isBlank()) ? "1m" : s.getTimeframe();
    }

    private int windowDays(MachineLearningInvestStrategySettings s) {
        Integer wd = s.getTrainingWindowDays();
        if (wd == null || wd <= 0) return 30; // дефолт из БД
        return wd;
    }


    private int tradeTopN(MachineLearningInvestStrategySettings s) {
        try {
            var m = s.getClass().getMethod("getTradeTopN");
            Object v = m.invoke(s);
            if (v instanceof Integer i && i > 0) return i;
        } catch (ReflectiveOperationException ignore) {}
        return Math.min(5, universeSize(s)); // дефолт: топ-5 или меньше
    }

    private BigDecimal buyThreshold(MachineLearningInvestStrategySettings s) {
        return s.getBuyThreshold() == null ? new BigDecimal("0.7") : s.getBuyThreshold();
    }

    private BigDecimal sellThreshold(MachineLearningInvestStrategySettings s) {
        try {
            var m = s.getClass().getMethod("getSellThreshold");
            Object v = m.invoke(s);
            if (v instanceof BigDecimal bd) return bd;
        } catch (ReflectiveOperationException ignore) {}
        return new BigDecimal("0.7");
    }

    private BigDecimal quota(MachineLearningInvestStrategySettings s) {
        return s.getQuota() == null ? new BigDecimal("100") : s.getQuota();
    }

    private int maxTrades(MachineLearningInvestStrategySettings s) {
        Integer v = s.getMaxTradesPerQuota();
        return (v == null || v <= 0) ? 3 : v;
    }

    private int universeSize(MachineLearningInvestStrategySettings s) {
        Integer v = s.getUniverseSize();
        return (v == null || v <= 0) ? 30 : v;
    }

    private BigDecimal min24hQuoteVolume(MachineLearningInvestStrategySettings s) {
        return s.getMin24hQuoteVolume() == null ? new BigDecimal("500000") : s.getMin24hQuoteVolume();
    }
}
