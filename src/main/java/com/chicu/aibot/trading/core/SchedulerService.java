package com.chicu.aibot.trading.core;

public interface SchedulerService {
    void startStrategy(Long chatId, String strategyName);
    void stopStrategy(Long chatId, String strategyName);
    boolean isStrategyActive(Long chatId, String strategyName);
}
