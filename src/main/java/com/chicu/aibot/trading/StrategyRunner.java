package com.chicu.aibot.trading;

public interface StrategyRunner {
    void start(Long chatId, String strategyName);
    void stop(Long chatId, String strategyName);
    boolean isRunning(Long chatId, String strategyName);
}
