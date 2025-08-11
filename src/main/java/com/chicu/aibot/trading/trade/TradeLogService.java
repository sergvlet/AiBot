package com.chicu.aibot.trading.trade;

import com.chicu.aibot.exchange.enums.OrderSide;

import java.time.Instant;
import java.util.Optional;

public interface TradeLogService {

    void logFilled(Long chatId, String symbol, OrderSide side,
                   double price, double quantity, Instant time);

    Optional<TradeLogEvent> getLastTrade(Long chatId, String symbol);
}
