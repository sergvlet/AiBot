package com.chicu.aibot.strategy.bollinger.repository;

import com.chicu.aibot.strategy.bollinger.model.BollingerStrategySettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BollingerStrategySettingsRepository extends JpaRepository<BollingerStrategySettings, Long> {}
