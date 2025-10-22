package com.chicu.aibot.strategy.fibonacci.repository;

import com.chicu.aibot.strategy.fibonacci.model.FibonacciGridStrategySettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FibonacciGridStrategySettingsRepository extends JpaRepository<FibonacciGridStrategySettings, Long> {
    Optional<FibonacciGridStrategySettings> findByChatId(Long chatId); // ✅ добавлено
}
