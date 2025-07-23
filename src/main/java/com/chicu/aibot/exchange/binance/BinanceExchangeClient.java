package com.chicu.aibot.exchange.binance;

import com.chicu.aibot.exchange.client.ExchangeClient;
import com.chicu.aibot.exchange.enums.NetworkType;
import com.chicu.aibot.exchange.util.HmacUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

@Component("BINANCE")
@RequiredArgsConstructor
public class BinanceExchangeClient implements ExchangeClient {

    @Value("${binance.api.mainnet-base-url}")
    private String mainnetBaseUrl;

    @Value("${binance.api.testnet-base-url}")
    private String testnetBaseUrl;

    private final RestTemplate rest;

    @Override
    public boolean testConnection(String publicKey, String secretKey, NetworkType network) {
        String baseUrl = network == NetworkType.MAINNET ? mainnetBaseUrl : testnetBaseUrl;
        long ts = Instant.now().toEpochMilli();
        String path = "/api/v3/account";
        String query = "timestamp=" + ts;
        String signature = HmacUtil.sha256Hex(secretKey, query);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-MBX-APIKEY", publicKey);

        HttpEntity<?> req = new HttpEntity<>(headers);
        String url = baseUrl + path + "?" + query + "&signature=" + signature;

        try {
            ResponseEntity<String> resp = rest.exchange(url, HttpMethod.GET, req, String.class);
            return resp.getStatusCode() == HttpStatus.OK;
        } catch (Exception ex) {
            return false;
        }
    }
}