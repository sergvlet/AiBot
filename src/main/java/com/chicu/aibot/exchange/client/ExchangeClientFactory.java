package com.chicu.aibot.exchange.client;

import com.chicu.aibot.exchange.enums.Exchange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ExchangeClientFactory {

    /**
     * Spring внедрит все бинт ExchangeClient,
     * ключ — имя бена (мы выставили @Component("BINANCE"), @Component("BYBIT")).
     */
    private final Map<String, ExchangeClient> clients;

    public ExchangeClient getClient(Exchange exchange) {
        ExchangeClient client = clients.get(exchange.name());
        if (client == null) {
            throw new IllegalArgumentException("Не реализован клиент для биржи " + exchange);
        }
        return client;
    }
}
