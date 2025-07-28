package com.chicu.aibot.exchange.binance;

import com.chicu.aibot.exchange.client.ExchangeClient;
import com.chicu.aibot.exchange.enums.NetworkType;
import com.chicu.aibot.exchange.util.HmacUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

@Slf4j
@Component("BINANCE")
@RequiredArgsConstructor
public class BinanceExchangeClient implements ExchangeClient {

    @Value("${binance.api.mainnet-base-url}")
    private String mainnetBaseUrl;

    @Value("${binance.api.testnet-base-url}")
    private String testnetBaseUrl;

    private final RestTemplate rest;
    private final ObjectMapper objectMapper = new ObjectMapper(); // можно внедрить через @Bean

    @Override
    public boolean testConnection(String publicKey, String secretKey, NetworkType network) {
        String baseUrl = (network == NetworkType.MAINNET ? mainnetBaseUrl : testnetBaseUrl).replaceAll("/+$", "");
        String path = "/api/v3/account";
        long ts = Instant.now().toEpochMilli();

        String query = "timestamp=" + ts;
        String signature = HmacUtil.sha256Hex(secretKey, query);
        String url = baseUrl + path + "?" + query + "&signature=" + signature;

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-MBX-APIKEY", publicKey);
        HttpEntity<Void> req = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> resp = rest.exchange(url, HttpMethod.GET, req, String.class);

            if (resp.getStatusCode() != HttpStatus.OK || resp.getBody() == null) {
                log.warn("Binance testConnection: статус не OK или тело отсутствует. Status={}, body={}", resp.getStatusCode(), resp.getBody());
                return false;
            }

            JsonNode json = objectMapper.readTree(resp.getBody());
            if (!json.has("accountType") && !json.has("balances")) {
                log.warn("Binance testConnection: тело ответа не содержит ожидаемых полей. Body={}", resp.getBody());
                return false;
            }

            log.info("✅ Подключение к Binance успешно. Ключ: {}", publicKey);
            return true;

        } catch (HttpClientErrorException.Unauthorized ex) {
            log.warn("❌ Binance: API-ключ или подпись недействительны: {}", ex.getMessage());
            return false;
        } catch (Exception ex) {
            log.error("❌ Ошибка при проверке подключения к Binance: {}", ex.getMessage(), ex);
            return false;
        }
    }
}
