package com.chicu.aibot.strategy.scalping.repository;

import com.chicu.aibot.strategy.scalping.model.ScalpingStrategySettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ScalpingStrategySettingsRepository extends JpaRepository<ScalpingStrategySettings, Long> {
    Optional<ScalpingStrategySettings> findByChatId(Long chatId); // ✅ добавлено
}
