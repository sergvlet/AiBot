package com.chicu.aibot.strategy.ml_invest.repository;

import com.chicu.aibot.strategy.ml_invest.model.MachineLearningInvestStrategySettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MachineLearningInvestStrategySettingsRepository extends JpaRepository<MachineLearningInvestStrategySettings, Long> {
    Optional<MachineLearningInvestStrategySettings> findByChatId(Long chatId); // ✅ добавлено
}
