package com.chicu.aibot.strategy.fibonacci.service;


import com.chicu.aibot.strategy.fibonacci.model.FibonacciGridStrategySettings;

public interface FibonacciGridStrategySettingsService {
    /**
     * Найти или создать с дефолтными параметрами.
     */
    FibonacciGridStrategySettings getOrCreate(Long chatId);

    /**
     * Сохранить изменённые настройки.
     */
    void save(FibonacciGridStrategySettings settings);
}
