package com.chicu.aibot.trading.scheduler.impl;

import com.chicu.aibot.strategy.StrategyRegistry;
import com.chicu.aibot.strategy.TradingStrategy;
import com.chicu.aibot.trading.scheduler.SchedulerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulerServiceImpl implements SchedulerService {

    private final StrategyRegistry strategyRegistry;

    /** Потоки для каждой стратегии и чата */
    private final Map<String, ScheduledFuture<?>> runningStrategies = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(10);

    @Override
    public void startStrategy(Long chatId, String strategyName) {
        String key = strategyKey(chatId, strategyName);

        if (runningStrategies.containsKey(key)) {
            log.warn("⚠️ Стратегия уже запущена: {}", key);
            return;
        }

        TradingStrategy strategy = strategyRegistry.getStrategyOrThrow(strategyName);
        strategy.start(chatId);

        ScheduledFuture<?> future = executor.scheduleAtFixedRate(() -> {
            try {
                // эмуляция обновления цены (в реальности нужно подписка на цену)
                double mockPrice = 100.0;
                strategy.onPriceUpdate(chatId, mockPrice);
            } catch (Exception e) {
                log.error("❌ Ошибка при обновлении цены для {}: {}", key, e.getMessage(), e);
            }
        }, 0, 10, TimeUnit.SECONDS);

        runningStrategies.put(key, future);
        log.info("✅ Стратегия {} запущена для chatId={}", strategyName, chatId);
    }

    @Override
    public void stopStrategy(Long chatId, String strategyName) {
        String key = strategyKey(chatId, strategyName);

        ScheduledFuture<?> future = runningStrategies.remove(key);
        if (future != null) {
            future.cancel(true);
        }

        TradingStrategy strategy = strategyRegistry.getStrategyOrThrow(strategyName);
        strategy.stop(chatId);
        log.info("⛔ Стратегия {} остановлена для chatId={}", strategyName, chatId);
    }

    @Override
    public boolean isStrategyActive(Long chatId, String strategyName) {
        String key = strategyKey(chatId, strategyName);
        ScheduledFuture<?> future = runningStrategies.get(key);
        return future != null && !future.isCancelled();
    }

    private String strategyKey(Long chatId, String name) {
        return chatId + ":" + name;
    }
}
