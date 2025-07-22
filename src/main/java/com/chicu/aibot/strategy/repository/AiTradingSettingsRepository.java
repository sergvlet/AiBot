// src/main/java/com/chicu/aibot/strategy/AiTradingSettingsRepository.java
package com.chicu.aibot.strategy.repository;

import com.chicu.aibot.strategy.model.AiTradingSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiTradingSettingsRepository extends JpaRepository<AiTradingSettings, Long> {
}
