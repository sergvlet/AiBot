package com.chicu.aibot.exchange.service.impl;

import com.chicu.aibot.exchange.enums.Exchange;
import com.chicu.aibot.exchange.enums.NetworkType;
import com.chicu.aibot.exchange.model.ExchangeApiKey;
import com.chicu.aibot.exchange.model.ExchangeApiKeyId;
import com.chicu.aibot.exchange.model.ExchangeSettings;
import com.chicu.aibot.exchange.repository.ExchangeApiKeyRepository;
import com.chicu.aibot.exchange.repository.ExchangeSettingsRepository;
import com.chicu.aibot.exchange.service.ExchangeSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExchangeSettingsServiceImpl implements ExchangeSettingsService {

    private final ExchangeSettingsRepository settingsRepo;
    private final ExchangeApiKeyRepository apiKeyRepo;

    @Override
    @Transactional(readOnly = true)
    public ExchangeSettings getOrCreate(Long chatId) {
        return settingsRepo.findById(chatId)
            .orElseGet(() -> settingsRepo.save(
                ExchangeSettings.builder()
                    .chatId(chatId)
                    .exchange(Exchange.BINANCE)
                    .network(NetworkType.MAINNET)
                    .build()
            ));
    }

    @Override
    @Transactional
    public void updateExchange(Long chatId, String exchangeCode) {
        var s = getOrCreate(chatId);
        s.setExchange(Exchange.valueOf(exchangeCode));
        settingsRepo.save(s);
    }

    @Override
    @Transactional
    public void updateNetwork(Long chatId, String networkCode) {
        var s = getOrCreate(chatId);
        s.setNetwork(NetworkType.valueOf(networkCode));
        settingsRepo.save(s);
    }

    @Override
    @Transactional
    public void saveApiKeys(Long chatId, String publicKey, String secretKey) {
        var settings = getOrCreate(chatId);
        var id = ExchangeApiKeyId.builder()
                .chatId(chatId)
                .exchange(settings.getExchange())
                .network(settings.getNetwork())
                .build();

        var entity = ExchangeApiKey.builder()
                .id(id)
                .publicKey(publicKey)
                .secretKey(secretKey)
                .build();

        apiKeyRepo.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasApiKeys(Long chatId) {
        var s = getOrCreate(chatId);
        var id = new ExchangeApiKeyId(
            chatId,
            s.getExchange(),
            s.getNetwork()
        );
        return apiKeyRepo.existsById(id);
    }

    @Override
    public boolean testConnection(Long chatId) {
        // TODO: реальная проверка по API
        return true;
    }
}
