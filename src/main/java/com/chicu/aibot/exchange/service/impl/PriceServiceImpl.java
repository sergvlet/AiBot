// src/main/java/com/chicu/aibot/exchange/service/impl/PriceServiceImpl.java
package com.chicu.aibot.exchange.service.impl;

import com.chicu.aibot.exchange.client.ExchangeClientFactory;
import com.chicu.aibot.exchange.enums.Exchange;
import com.chicu.aibot.exchange.enums.NetworkType;
import com.chicu.aibot.exchange.model.TickerInfo;
import com.chicu.aibot.exchange.service.PriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class PriceServiceImpl implements PriceService {

    private final ExchangeClientFactory clientFactory;

    @Override
    public BigDecimal getLastPrice(Exchange exchange, String symbol, NetworkType network) {
        var client = clientFactory.getClient(exchange);
        // В твоём проекте у TickerInfo, судя по коду AiSelectSymbolState, есть getPrice() и getChangePct().
        // Раньше стоял TickerInfo::getLastPrice — такого геттера нет. Меняем на getPrice().
        return client.getTicker(symbol, network)
                .map(TickerInfo::getPrice)
                .orElse(null);
    }
}
