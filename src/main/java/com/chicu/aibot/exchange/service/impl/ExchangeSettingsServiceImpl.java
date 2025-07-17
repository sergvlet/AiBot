package com.chicu.aibot.exchange.service.impl;


import com.chicu.aibot.exchange.Exchange;
import com.chicu.aibot.exchange.model.ExchangeSettings;
import com.chicu.aibot.exchange.repository.ExchangeSettingsRepository;
import com.chicu.aibot.exchange.service.ExchangeSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExchangeSettingsServiceImpl implements ExchangeSettingsService {

    private final ExchangeSettingsRepository repo;

    @Override
    @Transactional(readOnly = true)
    public ExchangeSettings getOrCreate(Long chatId) {
        return repo.findById(chatId)
                   .orElseGet(() -> {
                       ExchangeSettings s = ExchangeSettings.builder()
                               .chatId(chatId)
                               .exchange(Exchange.BINANCE) // дефолт
                               .build();
                       return repo.save(s);
                   });
    }

    @Override
    @Transactional
    public void updateExchange(Long chatId, String exchangeCode) {
        ExchangeSettings s = getOrCreate(chatId);
        Exchange ex = Exchange.valueOf(exchangeCode);
        s.setExchange(ex);
        repo.save(s);
    }
}
