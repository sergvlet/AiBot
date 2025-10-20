package com.chicu.aibot.strategy.ml_invest.repository;

import com.chicu.aibot.strategy.ml_invest.model.MachineLearningInvestStrategySettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MachineLearningInvestStrategySettingsRepository extends JpaRepository<MachineLearningInvestStrategySettings, Long> {
    Optional<MachineLearningInvestStrategySettings> findByChatId(Long chatId);
}
