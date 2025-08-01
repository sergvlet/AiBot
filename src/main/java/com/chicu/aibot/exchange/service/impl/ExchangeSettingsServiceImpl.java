package com.chicu.aibot.exchange.service.impl;

import com.chicu.aibot.exchange.client.ExchangeClient;
import com.chicu.aibot.exchange.enums.ConnectionStatus;
import com.chicu.aibot.exchange.enums.Exchange;
import com.chicu.aibot.exchange.enums.NetworkType;
import com.chicu.aibot.exchange.model.*;
import com.chicu.aibot.exchange.repository.*;
import com.chicu.aibot.exchange.service.ExchangeSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeSettingsServiceImpl implements ExchangeSettingsService {

    private final ExchangeSettingsRepository settingsRepo;
    private final ExchangeApiKeyRepository apiKeyRepo;
    private final Map<String, ExchangeClient> clients;

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
        ExchangeSettings s = getOrCreate(chatId);
        s.setExchange(Exchange.valueOf(exchangeCode));
        settingsRepo.save(s);
    }

    @Override
    @Transactional
    public void updateNetwork(Long chatId, String networkCode) {
        ExchangeSettings s = getOrCreate(chatId);
        s.setNetwork(NetworkType.valueOf(networkCode));
        settingsRepo.save(s);
    }

    @Override
    @Transactional
    public void saveApiKeys(Long chatId, String publicKey, String secretKey) {
        ExchangeSettings s = getOrCreate(chatId);
        ExchangeApiKeyId id = ExchangeApiKeyId.builder()
                .chatId(chatId)
                .exchange(s.getExchange())
                .network(s.getNetwork())
                .build();
        ExchangeApiKey bean = ExchangeApiKey.builder()
                .id(id)
                .publicKey(publicKey)
                .secretKey(secretKey)
                .build();
        apiKeyRepo.save(bean);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasApiKeys(Long chatId) {
        ExchangeSettings s = getOrCreate(chatId);
        ExchangeApiKeyId id = ExchangeApiKeyId.builder()
                .chatId(chatId)
                .exchange(s.getExchange())
                .network(s.getNetwork())
                .build();
        return apiKeyRepo.existsById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean testConnection(Long chatId) {
        return testConnectionVerbose(chatId) == ConnectionStatus.SUCCESS;
    }

    @Override
    @Transactional(readOnly = true)
    public ConnectionStatus testConnectionVerbose(Long chatId) {
        ExchangeSettings s = getOrCreate(chatId);
        ExchangeApiKeyId id = new ExchangeApiKeyId(chatId, s.getExchange(), s.getNetwork());

        return apiKeyRepo.findById(id)
                .map(keys -> {
                    ExchangeClient client = clients.get(s.getExchange().name());
                    if (client == null) {
                        log.warn("❌ Нет клиента для биржи: {}", s.getExchange());
                        return ConnectionStatus.CONNECTION_ERROR;
                    }

                    try {
                        return client.testConnection(keys.getPublicKey(), keys.getSecretKey(), s.getNetwork())
                                ? ConnectionStatus.SUCCESS
                                : ConnectionStatus.INVALID_KEY;
                    } catch (Exception ex) {
                        log.error("❌ Ошибка подключения к бирже {}: {}", s.getExchange(), ex.getMessage(), ex);
                        return ConnectionStatus.CONNECTION_ERROR;
                    }
                })
                .orElse(ConnectionStatus.NO_KEYS);
    }
    @Override
    @Transactional(readOnly = true)
    public ExchangeApiKey getApiKey(Long chatId) {
        ExchangeSettings s = getOrCreate(chatId);
        ExchangeApiKeyId id = ExchangeApiKeyId.builder()
                .chatId(chatId)
                .exchange(s.getExchange())
                .network(s.getNetwork())
                .build();

        return apiKeyRepo.findById(id)
                .orElseThrow(() -> new IllegalStateException("API keys not set for chat " + chatId));
    }

}
