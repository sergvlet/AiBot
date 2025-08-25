// src/main/java/com/chicu/aibot/exchange/mapper/OrderResponseMapper.java
package com.chicu.aibot.exchange.mapper;

import com.chicu.aibot.exchange.model.OrderResponse;

/**
 * Унифицированный маппер для преобразования "сырого" ответа от биржи
 * в OrderResponse.
 */
public interface OrderResponseMapper {
    OrderResponse map(Object rawResp);
}
