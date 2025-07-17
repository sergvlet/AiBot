// src/main/java/com/chicu/aibot/bot/strategy/StrategyType.java
package com.chicu.aibot.bot.strategy;

public enum StrategyType {
    SCALPING("Scalping"),
    FIBONACCI_GRID("Fibonacci Grid"),
    RSI_EMA("RSI + EMA"),
    MA_CROSSOVER("MA Crossover"),
    BOLLINGER_BANDS("Bollinger Bands");

    private final String label;
    StrategyType(String label) { this.label = label; }
    public String getLabel() { return label; }

    public static StrategyType findByCode(String code) {
        for (StrategyType t : values()) if (t.name().equals(code)) return t;
        throw new IllegalArgumentException("Unknown strategy code: " + code);
    }
}
