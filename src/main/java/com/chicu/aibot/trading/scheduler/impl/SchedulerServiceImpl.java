package com.chicu.aibot.trading.scheduler.impl;

import com.chicu.aibot.strategy.StrategyRegistry;
import com.chicu.aibot.strategy.TradingStrategy;
import com.chicu.aibot.strategy.fibonacci.model.FibonacciGridStrategySettings;
import com.chicu.aibot.strategy.fibonacci.repository.FibonacciGridStrategySettingsRepository;
import com.chicu.aibot.strategy.scalping.model.ScalpingStrategySettings;
import com.chicu.aibot.strategy.scalping.repository.ScalpingStrategySettingsRepository;
import com.chicu.aibot.trading.scheduler.SchedulerService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulerServiceImpl implements SchedulerService {

    private final StrategyRegistry registry;
    private final ScalpingStrategySettingsRepository scalpingRepo;
    private final FibonacciGridStrategySettingsRepository fibRepo;

    private ScheduledThreadPoolExecutor scheduler;
    private final Map<String, ScheduledFuture<?>> runningTasks = new ConcurrentHashMap<>();

    @PostConstruct
    private void init() {
        int threads = Math.max(2, Runtime.getRuntime().availableProcessors());
        this.scheduler = new ScheduledThreadPoolExecutor(threads);
        this.scheduler.setRemoveOnCancelPolicy(true);
        log.info("Планировщик инициализирован: {} поток(а/ов)", threads);
    }

    @PreDestroy
    private void shutdown() {
        log.info("Останавливаю планировщик…");
        scheduler.shutdownNow();
    }

    @Override
    public void startStrategy(Long chatId, String strategyName) {
        String key = buildKey(chatId, strategyName);

        ScheduledFuture<?> existing = runningTasks.get(key);
        if (existing != null && (existing.isCancelled() || existing.isDone())) {
            runningTasks.remove(key);
            existing = null;
        }
        if (existing != null) {
            log.info("Стратегия {} уже запущена для chatId={}", strategyName, chatId);
            return;
        }

        long intervalSec = resolveIntervalSec(chatId, strategyName);
        TradingStrategy strategy = registry.getStrategyOrThrow(strategyName);

        try {
            strategy.start(chatId);
        } catch (Exception e) {
            log.error("Ошибка start() у стратегии {} @{}: {}", strategyName, chatId, e.getMessage(), e);
            throw e;
        }

        ScheduledFuture<?> future = scheduleLoop(chatId, strategyName, strategy, intervalSec);
        runningTasks.put(key, future);
        log.info("Запущена {} для chatId={} (интервал={}s)", strategyName, chatId, intervalSec);
    }

    @Override
    public void stopStrategy(Long chatId, String strategyName) {
        String key = buildKey(chatId, strategyName);
        ScheduledFuture<?> future = runningTasks.remove(key);

        if (future == null || future.isCancelled() || future.isDone()) {
            log.info("Стратегия {} не запущена для chatId={}; останавливать нечего", strategyName, chatId);
            return;
        }

        future.cancel(true);
        try {
            registry.getStrategyOrThrow(strategyName).stop(chatId);
        } catch (Exception e) {
            log.error("Ошибка stop() у стратегии {} @{}: {}", strategyName, chatId, e.getMessage(), e);
        }
        log.info("Остановлена {} для chatId={}", strategyName, chatId);
    }

    /** Перезапускает стратегию с актуальным таймфреймом из настроек. */
    public void restartStrategy(Long chatId, String strategyName) {
        String key = buildKey(chatId, strategyName);
        TradingStrategy strategy = registry.getStrategyOrThrow(strategyName);

        // 1) Остановить, если была
        ScheduledFuture<?> old = runningTasks.remove(key);
        if (old != null && !old.isCancelled() && !old.isDone()) {
            old.cancel(true);
            try {
                strategy.stop(chatId);
            } catch (Exception e) {
                log.error("Ошибка stop() при перезапуске {} @{}: {}", strategyName, chatId, e.getMessage(), e);
            }
            log.info("Старая задача {} для chatId={} отменена", strategyName, chatId);
        } else {
            log.info("Стратегия {} для chatId={} не была запущена — перезапускаю как новую", strategyName, chatId);
        }

        // 2) Старт с новым интервалом
        long intervalSec = resolveIntervalSec(chatId, strategyName);
        try {
            strategy.start(chatId);
        } catch (Exception e) {
            log.error("Ошибка start() при перезапуске {} @{}: {}", strategyName, chatId, e.getMessage(), e);
            throw e;
        }

        ScheduledFuture<?> future = scheduleLoop(chatId, strategyName, strategy, intervalSec);
        runningTasks.put(key, future);
        log.info("Перезапущена {} для chatId={} (интервал={}s)", strategyName, chatId, intervalSec);
    }

    @Override
    public boolean isStrategyActive(Long chatId, String strategyName) {
        String key = buildKey(chatId, strategyName);
        ScheduledFuture<?> f = runningTasks.get(key);
        return f != null && !f.isDone() && !f.isCancelled();
    }

    private ScheduledFuture<?> scheduleLoop(Long chatId, String strategyName, TradingStrategy strategy, long intervalSec) {
        return scheduler.scheduleAtFixedRate(() -> {
            try {
                double price = strategy.getCurrentPrice(chatId);
                strategy.onPriceUpdate(chatId, price);
            } catch (Exception e) {
                log.error("Ошибка onPriceUpdate для {} @{}: {}", strategyName, chatId, e.getMessage(), e);
            }
        }, 0, intervalSec, TimeUnit.SECONDS);
    }

    private long resolveIntervalSec(Long chatId, String strategyName) {
        String tf;
        switch (strategyName) {
            case "SCALPING" -> {
                ScalpingStrategySettings s = scalpingRepo.findById(chatId)
                        .orElseThrow(() -> new IllegalStateException("Scalping settings not found for chatId=" + chatId));
                tf = s.getTimeframe();
            }
            case "FIBONACCI_GRID" -> {
                FibonacciGridStrategySettings f = fibRepo.findById(chatId)
                        .orElseThrow(() -> new IllegalStateException("FibonacciGrid settings not found for chatId=" + chatId));
                tf = f.getTimeframe();
            }
            default -> throw new IllegalArgumentException("Unknown strategy: " + strategyName);
        }
        return parseTimeframe(tf);
    }

    private long parseTimeframe(String tfRaw) {
        if (tfRaw == null || tfRaw.isBlank()) {
            throw new IllegalArgumentException("Пустой timeframe");
        }
        String tf = tfRaw.trim().toLowerCase();
        char unit = tf.charAt(tf.length() - 1);
        String num = tf.substring(0, tf.length() - 1);
        long value;
        try {
            value = Long.parseLong(num);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Некорректное число в timeframe: " + tfRaw, e);
        }
        return switch (unit) {
            case 's' -> Duration.ofSeconds(value).getSeconds();
            case 'm' -> Duration.ofMinutes(value).getSeconds();
            case 'h' -> Duration.ofHours(value).getSeconds();
            case 'd' -> Duration.ofDays(value).getSeconds();
            default -> throw new IllegalArgumentException("Неизвестная единица timeframe '" + unit + "' в " + tfRaw);
        };
    }

    private String buildKey(Long chatId, String strategyName) {
        return chatId + ":" + strategyName;
    }
}
