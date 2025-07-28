package com.chicu.aibot.strategy.core;

public interface RunnableStrategy extends Runnable {
    String getStrategyName();
    Long getChatId();
    void stop(); // Остановка стратегии
    boolean isRunning(); // Проверка статуса
}
