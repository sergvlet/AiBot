// src/main/java/com/chicu/aibot/exchange/repository/ExchangeApiKeyRepository.java
package com.chicu.aibot.exchange.repository;

import com.chicu.aibot.exchange.model.ExchangeApiKey;
import com.chicu.aibot.exchange.model.ExchangeApiKeyId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeApiKeyRepository
        extends JpaRepository<ExchangeApiKey, ExchangeApiKeyId> { }
