// src/main/java/com/chicu/aibot/exchange/client/ExchangeSymbolMetaClient.java
package com.chicu.aibot.exchange.client;

import com.chicu.aibot.exchange.enums.NetworkType;
import com.chicu.aibot.exchange.model.SymbolFilters;

public interface ExchangeSymbolMetaClient {
    SymbolFilters getSymbolFilters(String apiKey, String secretKey, NetworkType network, String symbol);
}
