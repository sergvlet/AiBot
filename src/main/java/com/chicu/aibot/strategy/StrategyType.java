package com.chicu.aibot.strategy;

import lombok.Getter;

@Getter
public enum StrategyType {
    SCALPING("Scalping"),
    FIBONACCI_GRID("Fibonacci Grid"),
    RSI_EMA("RSI + EMA"),
    MA_CROSSOVER("MA Crossover"),
    BOLLINGER_BANDS("Bollinger Bands"),
    MACHINE_LEARNING_INVEST("Machine Learning Invest");


    private final String label;
    StrategyType(String label) { this.label = label; }

    public static StrategyType findByCode(String code) {
        for (StrategyType t : values()) if (t.name().equals(code)) return t;
        throw new IllegalArgumentException("Unknown strategy code: " + code);
    }
}
