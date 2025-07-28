package com.chicu.aibot.strategy.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Service
public class StrategyRunnerService {

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, Future<?>> runningStrategies = new ConcurrentHashMap<>();

    public void start(RunnableStrategy strategy) {
        String key = buildKey(strategy);
        if (runningStrategies.containsKey(key)) {
            log.warn("⚠️ Стратегия уже запущена: {}", key);
            return;
        }

        log.info("▶️ Запуск стратегии: {}", key);
        Future<?> future = executor.submit(strategy);
        runningStrategies.put(key, future);
    }

    public void stop(RunnableStrategy strategy) {
        String key = buildKey(strategy);
        Future<?> future = runningStrategies.remove(key);
        if (future != null) {
            strategy.stop(); // флаг завершения
            future.cancel(true);
            log.info("⏹ Остановлена стратегия: {}", key);
        }
    }

    public boolean isRunning(RunnableStrategy strategy) {
        String key = buildKey(strategy);
        return runningStrategies.containsKey(key);
    }

    private String buildKey(RunnableStrategy s) {
        return s.getChatId() + ":" + s.getStrategyName();
    }
}
