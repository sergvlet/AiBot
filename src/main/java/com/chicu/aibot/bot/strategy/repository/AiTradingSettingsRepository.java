// src/main/java/com/chicu/aibot/strategy/AiTradingSettingsRepository.java
package com.chicu.aibot.bot.strategy.repository;

import com.chicu.aibot.bot.strategy.model.AiTradingSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiTradingSettingsRepository extends JpaRepository<AiTradingSettings, Long> {
}
