package com.chicu.aibot.strategy.bollinger.repository;

import com.chicu.aibot.strategy.bollinger.model.BollingerStrategySettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BollingerStrategySettingsRepository extends JpaRepository<BollingerStrategySettings, Long> {
    Optional<BollingerStrategySettings> findByChatId(Long chatId); // ✅ добавлено
}
