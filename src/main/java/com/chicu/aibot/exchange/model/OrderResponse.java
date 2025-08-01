// src/main/java/com/chicu/aibot/exchange/model/OrderResponse.java
package com.chicu.aibot.exchange.model;

import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {
    private String orderId;
    private String symbol;
    private String status;
    private BigDecimal executedQty;
    private Instant transactTime;
}
