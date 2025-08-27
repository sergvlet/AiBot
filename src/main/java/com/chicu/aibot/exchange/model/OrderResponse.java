// src/main/java/com/chicu/aibot/exchange/model/OrderResponse.java
package com.chicu.aibot.exchange.model;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Унифицированный ответ на размещение ордера.
 * Поддерживает LIMIT и MARKET ордера с учётом цены, объёма и комиссий.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {

    private String orderId;          // ID ордера на бирже
    private String symbol;           // тикер, например BTCUSDT
    private String status;           // статус ордера (NEW, FILLED и т.д.)

    private BigDecimal price;        // цена: LIMIT = лимитная, MARKET = средняя фактическая
    private BigDecimal executedQty;  // исполненное количество (BASE asset)
    private BigDecimal origQty;      // исходный объём (BASE asset), если доступен
    private BigDecimal avgPrice;     // средняя цена исполнения (если доступно)
    private BigDecimal quoteQty;     // общая сумма сделки в QUOTE валюте (например, USDT) ✅

    private BigDecimal commission;   // комиссия
    private String commissionAsset;  // валюта комиссии (например, USDT, BNB)

    private Instant transactTime;    // время сделки
}
