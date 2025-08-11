package com.chicu.aibot.trading.trade.impl;

import com.chicu.aibot.exchange.enums.OrderSide;
import com.chicu.aibot.trading.trade.TradeLogEvent;
import com.chicu.aibot.trading.trade.TradeLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
public class InMemoryTradeLogService implements TradeLogService {

    private final ConcurrentMap<String, TradeLogEvent> lastByChatAndSymbol = new ConcurrentHashMap<>();

    private static String key(Long chatId, String symbol) {
        return chatId + ":" + (symbol == null ? "" : symbol.toUpperCase());
    }

    @Override
    public void logFilled(Long chatId, String symbol, OrderSide side,
                          double price, double quantity, Instant time) {
        TradeLogEvent ev = TradeLogEvent.builder()
                .chatId(chatId).symbol(symbol).side(side)
                .price(price).quantity(quantity)
                .time(time == null ? Instant.now() : time)
                .build();
        lastByChatAndSymbol.put(key(chatId, symbol), ev);
        log.debug("Trade logged: {}", ev);
    }

    @Override
    public Optional<TradeLogEvent> getLastTrade(Long chatId, String symbol) {
        return Optional.ofNullable(lastByChatAndSymbol.get(key(chatId, symbol)));
    }
}
