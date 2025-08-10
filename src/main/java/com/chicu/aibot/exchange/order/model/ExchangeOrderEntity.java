package com.chicu.aibot.exchange.order.model;

import com.chicu.aibot.exchange.enums.NetworkType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "exchange_orders",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_chat_exchange_network_order",
                        columnNames = {"chat_id", "exchange", "network", "order_id"})
        },
        indexes = {
                @Index(name = "idx_orders_chat_symbol_status", columnList = "chat_id,symbol,status"),
                @Index(name = "idx_orders_chat_symbol_side_status", columnList = "chat_id,symbol,side,status")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExchangeOrderEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;
    @Column(name = "exchange", nullable = false, length = 32)
    private String exchange;
    @Enumerated(EnumType.STRING)
    @Column(name = "network", nullable = false, length = 16)
    private NetworkType network;

    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;
    @Column(name = "symbol", nullable = false, length = 32)
    private String symbol;
    @Column(name = "side", nullable = false, length = 8)
    private String side; // BUY/SELL
    @Column(name = "type", nullable = false, length = 16)
    private String type; // LIMIT/MARKET

    @Column(name = "price", precision = 38, scale = 18)
    private BigDecimal price;
    @Column(name = "quantity", precision = 38, scale = 18)
    private BigDecimal quantity;
    @Column(name = "executed_qty", precision = 38, scale = 18)
    private BigDecimal executedQty;

    @Column(name = "status", nullable = false, length = 32)
    private String status; // NEW, PARTIALLY_FILLED, FILLED...

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;
}
