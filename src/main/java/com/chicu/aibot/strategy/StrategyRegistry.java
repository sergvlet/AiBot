package com.chicu.aibot.strategy;

import com.chicu.aibot.strategy.fibonacci.FibonacciGridStrategy;
import com.chicu.aibot.strategy.scalping.ScalpingStrategy;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Реестр всех стратегий по их типу.
 * Позволяет запускать стратегии по имени, без if/else.
 */
@Component
@RequiredArgsConstructor
public class StrategyRegistry {

    private final FibonacciGridStrategy fibonacciGridStrategy;
    private final ScalpingStrategy scalpingStrategy;

    private final Map<StrategyType, TradingStrategy> strategyMap = new EnumMap<>(StrategyType.class);

    @PostConstruct
    public void init() {
        strategyMap.put(StrategyType.FIBONACCI_GRID, fibonacciGridStrategy);
        strategyMap.put(StrategyType.SCALPING, scalpingStrategy);
    }

    /** Получение стратегии по enum-типу */
    public TradingStrategy get(StrategyType type) {
        TradingStrategy strategy = strategyMap.get(type);
        if (strategy == null) {
            throw new IllegalArgumentException("❌ Стратегия не найдена: " + type);
        }
        return strategy;
    }

    /** Получение стратегии по строковому имени, с преобразованием в enum */
    public TradingStrategy getStrategyOrThrow(String strategyName) {
        try {
            StrategyType type = StrategyType.valueOf(strategyName);
            return get(type);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("❌ Неизвестное имя стратегии: " + strategyName);
        }
    }
}
