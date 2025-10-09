// src/main/java/com/chicu/aibot/exchange/service/impl/SymbolFiltersServiceImpl.java
package com.chicu.aibot.exchange.service.impl;

import com.chicu.aibot.exchange.client.ExchangeClientFactory;
import com.chicu.aibot.exchange.client.ExchangeSymbolMetaClient;
import com.chicu.aibot.exchange.enums.Exchange;
import com.chicu.aibot.exchange.enums.NetworkType;
import com.chicu.aibot.exchange.model.SymbolFilters;
import com.chicu.aibot.exchange.service.SymbolFiltersService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class SymbolFiltersServiceImpl implements SymbolFiltersService {

    private final ExchangeClientFactory clientFactory;
    // Удалили зависимость от ExchangeSettingsService — chatId/ключи не нужны для exchangeInfo.

    /**
     * Возвращает реальные фильтры из клиента, если тот их поддерживает (через ExchangeSymbolMetaClient).
     * Если нет — возвращаем безопасные дефолты (только stepSize для округления).
     */
    @Override
    public SymbolFilters getFilters(Exchange exchange, String symbol, NetworkType network) {
        var client = clientFactory.getClient(exchange);

        if (client instanceof ExchangeSymbolMetaClient meta) {
            // Большинство бирж отдают exchangeInfo без аутентификации — передаём null-ключи.
            SymbolFilters filters = meta.getSymbolFilters(null, null, network, symbol);
            if (filters != null) {
                return filters;
            }
        }

        // Дефолты: даём только stepSize (для корректного округления qty).
        return SymbolFilters.builder()
                .stepSize(new BigDecimal("0.00000001"))
                .minQty(null)
                .minNotional(null)
                .build();
    }
}
