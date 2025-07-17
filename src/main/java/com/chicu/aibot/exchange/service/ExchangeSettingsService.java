package com.chicu.aibot.exchange.service;


import com.chicu.aibot.exchange.model.ExchangeSettings;

public interface ExchangeSettingsService {
    ExchangeSettings getOrCreate(Long chatId);
    void updateExchange(Long chatId, String exchangeCode);
}
