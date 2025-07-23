package com.chicu.aibot.exchange.bybit;

import com.chicu.aibot.exchange.client.ExchangeClient;
import com.chicu.aibot.exchange.enums.NetworkType;
import com.chicu.aibot.exchange.util.HmacUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;

@Component("BYBIT")
@RequiredArgsConstructor
public class BybitExchangeClient implements ExchangeClient {

    @Value("${bybit.api.mainnet-base-url}")
    private String mainnetBaseUrl;

    @Value("${bybit.api.testnet-base-url}")
    private String testnetBaseUrl;

    private final RestTemplate rest;

    @Override
    public boolean testConnection(String publicKey, String secretKey, NetworkType network) {
        // 1) Выбираем базовый URL
        String base = (network == NetworkType.MAINNET ? mainnetBaseUrl : testnetBaseUrl)
                .replaceAll("/+$", "");

        // 2) Эндпоинт V5
        String url = base + "/v5/account/wallet-balance";

        // 3) Общие параметры
        String accountType = "SPOT";
        long ts = Instant.now().toEpochMilli();
        long recvWindow = 5000;

        // 4) Строка для подписи (алфавитный порядок полей!)
        //    accountType, recvWindow, timestamp
        String toSign = "accountType=" + accountType +
                "&recvWindow=" + recvWindow +
                "&timestamp=" + ts;
        String signature = HmacUtil.sha256Hex(secretKey, toSign);

        // 5) Заголовки X-BAPI-*
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-BAPI-API-KEY", publicKey);
        headers.set("X-BAPI-SIGN", signature);
        headers.set("X-BAPI-TIMESTAMP", String.valueOf(ts));
        headers.set("X-BAPI-RECV-WINDOW", String.valueOf(recvWindow));

        // 6) Тело запроса — только accountType
        Map<String, String> body = Map.of("accountType", accountType);

        HttpEntity<Map<String,String>> req = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> resp = rest.exchange(url, HttpMethod.POST, req, String.class);
            if (resp.getStatusCode() != HttpStatus.OK || resp.getBody() == null) {
                return false;
            }
            // По спецификации: {"retCode":0,...}
            return resp.getBody().contains("\"retCode\":0");
        } catch (Exception ex) {
            return false;
        }
    }
}