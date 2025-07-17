package com.chicu.aibot.bot.strategy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Router, который по StrategyType возвращает нужную реализацию TradingStrategy.
 */
@Component
public class StrategyRouter {

    private final Map<StrategyType, TradingStrategy> router;

    /**
     * Единственный конструктор — Spring подставит сюда все бины, которые реализуют TradingStrategy.
     */
    public StrategyRouter(List<TradingStrategy> strategies) {
        this.router = strategies.stream()
                .collect(Collectors.toMap(TradingStrategy::getType, Function.identity()));
    }

    /**
     * Возвращает стратегию по её типу.
     */
    public TradingStrategy get(StrategyType type) {
        TradingStrategy s = router.get(type);
        if (s == null) throw new IllegalArgumentException("No strategy for type " + type);
        return s;
    }
}
