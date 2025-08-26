package com.chicu.aibot.exchange.order.model;

import com.chicu.aibot.exchange.enums.NetworkType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "exchange_orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeOrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long chatId;

    private String exchange;

    @Enumerated(EnumType.STRING)
    private NetworkType network;

    private String orderId;
    private String symbol;
    private String side;   // BUY / SELL
    private String type;   // MARKET / LIMIT
    private String status; // NEW, FILLED, PARTIALLY_FILLED, CANCELED, REJECTED

    private BigDecimal price;       // цена ордера
    private BigDecimal quantity;    // заказанное количество
    private BigDecimal executedQty; // реально исполнено
    private BigDecimal quoteQty;    // quote volume

    private BigDecimal commission;
    private String commissionAsset;

    // ==== Добавляем PnL ====
    private BigDecimal pnl;     // прибыль в quote
    private BigDecimal pnlPct;  // прибыль в процентах

    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastCheckedAt;
}
