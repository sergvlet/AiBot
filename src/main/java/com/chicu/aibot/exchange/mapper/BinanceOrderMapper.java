// src/main/java/com/chicu/aibot/exchange/mapper/BinanceOrderMapper.java
package com.chicu.aibot.exchange.mapper;

import com.chicu.aibot.exchange.model.OrderResponse;
import com.chicu.aibot.exchange.model.OrderInfo;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Slf4j
public class BinanceOrderMapper implements OrderResponseMapper {

    @Override
    public OrderResponse map(Object rawResp) {
        if (rawResp instanceof OrderInfo resp) {
            // ✅ Маппинг из нашего OrderInfo
            return OrderResponse.builder()
                    .orderId(resp.getOrderId())
                    .symbol(resp.getSymbol())
                    .status(resp.getStatus())
                    .price(resp.getPrice())
                    .executedQty(resp.getExecutedQty())
                    .quoteQty(resp.getAvgPrice() != null && resp.getExecutedQty() != null
                            ? resp.getAvgPrice().multiply(resp.getExecutedQty())
                            : BigDecimal.ZERO)
                    .commission(resp.getCommission() != null ? resp.getCommission() : BigDecimal.ZERO)
                    .commissionAsset(resp.getCommissionAsset() != null ? resp.getCommissionAsset() : "USDT")
                    .transactTime(resp.getUpdateTime() != null ? resp.getUpdateTime() : Instant.now())
                    .build();
        }

        if (rawResp instanceof Map<?, ?> map) {
            // ✅ Маппинг из JSON-ответа Binance
            try {
                return OrderResponse.builder()
                        .orderId(String.valueOf(map.get("orderId")))
                        .symbol(String.valueOf(map.get("symbol")))
                        .status(String.valueOf(map.get("status")))
                        .price(map.get("price") != null ? new BigDecimal(map.get("price").toString()) : BigDecimal.ZERO)
                        .executedQty(map.get("executedQty") != null ? new BigDecimal(map.get("executedQty").toString()) : BigDecimal.ZERO)
                        .quoteQty(map.get("cummulativeQuoteQty") != null ? new BigDecimal(map.get("cummulativeQuoteQty").toString()) : BigDecimal.ZERO)
                        .commission(BigDecimal.ZERO) // Binance отдаёт комиссии только через fills[]
                        .commissionAsset("USDT")
                        .transactTime(map.get("transactTime") != null
                                ? Instant.ofEpochMilli(Long.parseLong(map.get("transactTime").toString()))
                                : Instant.now())
                        .build();
            } catch (Exception e) {
                log.error("Ошибка парсинга Binance OrderResponse: {}", map, e);
                throw new IllegalArgumentException("Невозможно распарсить OrderResponse от Binance", e);
            }
        }

        throw new IllegalArgumentException("Ожидался OrderInfo или Map от Binance, но пришло: " +
                                           (rawResp == null ? "null" : rawResp.getClass().getName()));
    }
}
