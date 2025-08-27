// src/main/java/com/chicu/aibot/exchange/mapper/BybitOrderMapper.java
package com.chicu.aibot.exchange.mapper;

import com.chicu.aibot.exchange.model.OrderResponse;
import com.chicu.aibot.exchange.model.OrderInfo;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Slf4j
public class BybitOrderMapper implements OrderResponseMapper {

    @Override
    public OrderResponse map(Object rawResp) {
        if (rawResp instanceof OrderInfo resp) {
            // ✅ Маппинг из OrderInfo (наша DTO)
            return OrderResponse.builder()
                    .orderId(resp.getOrderId())
                    .symbol(resp.getSymbol())
                    .status(resp.getStatus())
                    .price(resp.getPrice())
                    .executedQty(resp.getExecutedQty())
                    .quoteQty(resp.getQuoteQty() != null ? resp.getQuoteQty() : BigDecimal.ZERO)
                    .commission(resp.getCommission() != null ? resp.getCommission() : BigDecimal.ZERO)
                    .commissionAsset(resp.getCommissionAsset() != null ? resp.getCommissionAsset() : "USDT")
                    .transactTime(resp.getUpdateTime() != null ? resp.getUpdateTime() : Instant.now())
                    .build();
        }

        if (rawResp instanceof Map<?, ?> map) {
            // ✅ Маппинг из JSON ответа Bybit V5
            try {
                return OrderResponse.builder()
                        .orderId(String.valueOf(map.get("orderId")))
                        .symbol(String.valueOf(map.get("symbol")))
                        .status(String.valueOf(map.get("orderStatus")))
                        .price(map.get("avgPrice") != null ? new BigDecimal(map.get("avgPrice").toString()) : BigDecimal.ZERO)
                        .executedQty(map.get("cumExecQty") != null ? new BigDecimal(map.get("cumExecQty").toString()) : BigDecimal.ZERO)
                        .quoteQty(map.get("cumExecValue") != null ? new BigDecimal(map.get("cumExecValue").toString()) : BigDecimal.ZERO)
                        .commission(map.get("cumExecFee") != null ? new BigDecimal(map.get("cumExecFee").toString()) : BigDecimal.ZERO)
                        .commissionAsset("USDT") // Bybit возвращает комиссию всегда в USDT/USDC
                        .transactTime(map.get("updatedTime") != null
                                ? Instant.ofEpochMilli(Long.parseLong(map.get("updatedTime").toString()))
                                : Instant.now())
                        .build();
            } catch (Exception e) {
                log.error("Ошибка парсинга Bybit OrderResponse: {}", map, e);
                throw new IllegalArgumentException("Невозможно распарсить OrderResponse от Bybit", e);
            }
        }

        throw new IllegalArgumentException("Ожидался OrderInfo или Map от Bybit, но пришло: " +
                (rawResp == null ? "null" : rawResp.getClass().getName()));
    }
}
