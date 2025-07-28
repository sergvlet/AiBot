package com.chicu.aibot.trading;

import com.chicu.aibot.strategy.StrategyRegistry;
import com.chicu.aibot.strategy.StrategyType;
import com.chicu.aibot.strategy.TradingStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyTaskManager implements StrategyRunner {

    private final StrategyRegistry registry;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private final Map<String, Future<?>> runningTasks = new ConcurrentHashMap<>();

    @Override
    public void start(Long chatId, String strategyName) {
        String key = key(chatId, strategyName);
        if (runningTasks.containsKey(key)) {
            log.warn("⚠️ Стратегия уже запущена: {}", key);
            return;
        }

        TradingStrategy strategy = registry.get(StrategyType.valueOf(strategyName));
        Future<?> future = executor.submit(() -> {
            strategy.start(chatId);
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // Периодический вызов обновления цены (эмулированное поведение)
                    double price = strategy.getCurrentPrice(chatId); // реализовать в конкретной стратегии
                    strategy.onPriceUpdate(chatId, price);
                    Thread.sleep(5000); // каждые 5 секунд
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("❌ Ошибка в стратегии {}: {}", key, e.getMessage(), e);
                }
            }
            strategy.stop(chatId);
        });

        runningTasks.put(key, future);
        log.info("▶️ Стратегия запущена: {}", key);
    }

    @Override
    public void stop(Long chatId, String strategyName) {
        String key = key(chatId, strategyName);
        Future<?> task = runningTasks.remove(key);
        if (task != null) {
            task.cancel(true);
            log.info("⏹️ Стратегия остановлена: {}", key);
        }
    }

    @Override
    public boolean isRunning(Long chatId, String strategyName) {
        String key = key(chatId, strategyName);
        Future<?> task = runningTasks.get(key);
        return task != null && !task.isDone() && !task.isCancelled();
    }

    private String key(Long chatId, String strategyName) {
        return chatId + ":" + strategyName;
    }
}
