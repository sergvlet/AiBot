// src/main/java/com/chicu/aibot/exchange/service/PriceService.java
package com.chicu.aibot.exchange.service;

import com.chicu.aibot.exchange.enums.Exchange;
import com.chicu.aibot.exchange.enums.NetworkType;

import java.math.BigDecimal;

public interface PriceService {
    BigDecimal getLastPrice(Exchange exchange, String symbol, NetworkType network);
}
