package com.chicu.aibot.exchange.client;

import com.chicu.aibot.exchange.enums.NetworkType;

public interface ExchangeClient {
    /**
     * @return true, если publicKey/secretKey для данной сети корректны
     */
    boolean testConnection(String publicKey, String secretKey, NetworkType network);
}
