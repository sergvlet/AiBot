// src/main/java/com/chicu/aibot/exchange/service/SymbolFiltersService.java
package com.chicu.aibot.exchange.service;

import com.chicu.aibot.exchange.enums.Exchange;
import com.chicu.aibot.exchange.enums.NetworkType;
import com.chicu.aibot.exchange.model.SymbolFilters;

public interface SymbolFiltersService {
    SymbolFilters getFilters(Exchange exchange, String symbol, NetworkType network);
}
