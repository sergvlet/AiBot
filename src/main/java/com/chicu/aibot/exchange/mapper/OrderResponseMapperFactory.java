// src/main/java/com/chicu/aibot/exchange/mapper/OrderResponseMapperFactory.java
package com.chicu.aibot.exchange.mapper;

import com.chicu.aibot.exchange.enums.Exchange;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Фабрика, которая по типу биржи возвращает правильный маппер.
 */
@Component
public class OrderResponseMapperFactory {

    private final Map<Exchange, OrderResponseMapper> mappers = new EnumMap<>(Exchange.class);

    public OrderResponseMapperFactory() {
        mappers.put(Exchange.BINANCE, new BinanceOrderMapper());
        mappers.put(Exchange.BYBIT, new BybitOrderMapper());
    }

    public OrderResponseMapper getMapper(Exchange exchange) {
        return mappers.getOrDefault(exchange, raw -> {
            throw new UnsupportedOperationException("Маппер для " + exchange + " не реализован");
        });
    }
}
