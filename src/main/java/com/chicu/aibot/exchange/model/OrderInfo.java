package com.chicu.aibot.exchange.model;

import com.chicu.aibot.exchange.enums.OrderSide;
import com.chicu.aibot.exchange.enums.OrderType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class OrderInfo {
    private String orderId;
    private String symbol;
    private String status;          // NEW / PARTIALLY_FILLED / FILLED / CANCELED / EXPIRED / REJECTED...
    private BigDecimal executedQty; // исполнено
    private BigDecimal origQty;     // исходно запрошено
    private BigDecimal price;       // лимитная цена (для LIMIT)
    private OrderSide side;
    private OrderType type;
    private Instant   updateTime;
    private BigDecimal avgPrice; // если удастся вычислить/получить
}
