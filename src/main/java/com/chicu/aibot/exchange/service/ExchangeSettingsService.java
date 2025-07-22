package com.chicu.aibot.exchange.service;


import com.chicu.aibot.exchange.model.ExchangeSettings;

public interface ExchangeSettingsService {
    ExchangeSettings getOrCreate(Long chatId);
    void updateExchange(Long chatId, String exchangeCode);
    void updateNetwork(Long chatId, String networkCode);
    void saveApiKeys(Long chatId, String publicKey, String secretKey);
    boolean hasApiKeys(Long chatId);
    boolean testConnection(Long chatId);
}
