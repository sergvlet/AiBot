package com.chicu.aibot.trading.scheduler;

public interface SchedulerService {
    void startStrategy(Long chatId, String strategyName);
    void stopStrategy(Long chatId, String strategyName);
    boolean isStrategyActive(Long chatId, String strategyName);
}
