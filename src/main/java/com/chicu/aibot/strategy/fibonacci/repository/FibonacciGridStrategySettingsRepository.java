package com.chicu.aibot.strategy.fibonacci.repository;

import com.chicu.aibot.strategy.fibonacci.model.FibonacciGridStrategySettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FibonacciGridStrategySettingsRepository
        extends JpaRepository<FibonacciGridStrategySettings, Long> {
}
