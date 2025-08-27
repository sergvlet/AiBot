package com.chicu.aibot.exchange.model;

import com.chicu.aibot.exchange.enums.OrderSide;
import com.chicu.aibot.exchange.enums.OrderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Унифицированная информация об ордере, полученная от биржи.
 * Используется как сырой объект, который потом приводится к OrderResponse через мапперы.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderInfo {

    private String orderId;           // ID ордера на бирже
    private String symbol;            // тикер (например BTCUSDT)
    private String status;            // NEW / PARTIALLY_FILLED / FILLED / CANCELED / EXPIRED / REJECTED...

    private BigDecimal origQty;       // изначально запрошено (BASE asset)
    private BigDecimal executedQty;   // фактически исполнено (BASE asset)

    private BigDecimal price;         // лимитная цена (для LIMIT)
    private BigDecimal avgPrice;      // средняя цена исполнения (для MARKET)
    private BigDecimal quoteQty;      // фактически потрачено/получено в QUOTE (например USDT)

    private OrderSide side;           // BUY / SELL
    private OrderType type;           // MARKET / LIMIT
    private Instant updateTime;       // время обновления на бирже

    private BigDecimal commission;    // сумма комиссии
    private String commissionAsset;   // валюта комиссии (например USDT, BNB и т.п.)
}
