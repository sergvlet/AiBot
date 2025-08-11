package com.chicu.aibot.trading.trade;

import com.chicu.aibot.exchange.enums.OrderSide;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class TradeLogEvent {
    Long chatId;
    String symbol;         // например "BTCUSDT"
    OrderSide side;        // BUY/SELL (из exchange.enums)
    double price;          // цена входа
    double quantity;       // исполненный объём
    Instant time;          // когда зафиксировали сделку
}
