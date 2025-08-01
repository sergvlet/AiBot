// src/main/java/com/chicu/aibot/exchange/model/OrderRequest.java
package com.chicu.aibot.exchange.model;

import lombok.*;
import java.math.BigDecimal;
import com.chicu.aibot.exchange.enums.OrderSide;
import com.chicu.aibot.exchange.enums.OrderType;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderRequest {
    private String symbol;
    private OrderSide side;
    private OrderType type;
    private BigDecimal quantity;
    private BigDecimal price;
}
