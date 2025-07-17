package com.chicu.aibot.bot.strategy.fibonacci.repository;

import com.chicu.aibot.bot.strategy.fibonacci.model.FibonacciGridStrategySettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FibonacciGridStrategySettingsRepository
        extends JpaRepository<FibonacciGridStrategySettings, Long> {
}
