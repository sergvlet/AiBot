package com.chicu.aibot.exchange.repository;

import com.chicu.aibot.exchange.model.ExchangeSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeSettingsRepository extends JpaRepository<ExchangeSettings, Long> {
}
