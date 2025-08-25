// src/main/java/com/chicu/aibot/exchange/mapper/BybitOrderMapper.java
package com.chicu.aibot.exchange.mapper;

import com.chicu.aibot.exchange.model.OrderResponse;
import com.chicu.aibot.exchange.model.OrderInfo;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;

@Slf4j
public class BybitOrderMapper implements OrderResponseMapper {

    @Override
    public OrderResponse map(Object rawResp) {
        if (!(rawResp instanceof OrderInfo resp)) {
            throw new IllegalArgumentException("Ожидался OrderInfo от Bybit");
        }

        return OrderResponse.builder()
                .orderId(resp.getOrderId())
                .symbol(resp.getSymbol())
                .status(resp.getStatus())
                .price(resp.getPrice())
                .executedQty(resp.getExecutedQty())
                .commission(resp.getCommission() != null ? resp.getCommission() : BigDecimal.ZERO)
                .commissionAsset(resp.getCommissionAsset() != null ? resp.getCommissionAsset() : "USDT")
                .transactTime(resp.getUpdateTime() != null ? resp.getUpdateTime() : Instant.now())
                .build();
    }
}
