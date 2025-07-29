package com.chicu.aibot.strategy.scalping.repository;

import com.chicu.aibot.strategy.scalping.model.ScalpingStrategySettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScalpingStrategySettingsRepository extends JpaRepository<ScalpingStrategySettings, Long> {
}
