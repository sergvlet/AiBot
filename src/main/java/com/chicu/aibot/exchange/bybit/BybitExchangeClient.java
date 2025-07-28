package com.chicu.aibot.exchange.bybit;

import com.chicu.aibot.exchange.client.ExchangeClient;
import com.chicu.aibot.exchange.enums.NetworkType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Slf4j
@Component("BYBIT")
@RequiredArgsConstructor
public class BybitExchangeClient implements ExchangeClient {

    @Value("${bybit.api.mainnet-base-url}")
    private String mainnetBaseUrl;

    @Value("${bybit.api.testnet-base-url}")
    private String testnetBaseUrl;

    private final RestTemplate rest;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean testConnection(String publicKey, String secretKey, NetworkType network) {
        try {
            String baseUrl = (network == NetworkType.MAINNET ? mainnetBaseUrl : testnetBaseUrl).replaceAll("/+$", "");
            String endpoint = "/v5/account/wallet-balance";
            String accountType = "UNIFIED";
            long timestamp = Instant.now().toEpochMilli();
            String recvWindow = "5000";

            String query = "accountType=" + accountType + "&recvWindow=" + recvWindow + "&timestamp=" + timestamp;
            String url = baseUrl + endpoint + "?" + query;

            // 🔒 Bybit требует stringToSign = timestamp + apiKey + recvWindow + query
            String stringToSign = timestamp + publicKey + recvWindow + query;
            String signature = hmacSha256(secretKey, stringToSign);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-BAPI-API-KEY", publicKey);
            headers.set("X-BAPI-SIGN", signature);
            headers.set("X-BAPI-TIMESTAMP", String.valueOf(timestamp));
            headers.set("X-BAPI-RECV-WINDOW", recvWindow);

            ResponseEntity<String> resp = rest.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            if (resp.getStatusCode() != HttpStatus.OK || resp.getBody() == null) {
                log.warn("❌ Bybit: статус не OK или тело пустое: status={}, body={}", resp.getStatusCode(), resp.getBody());
                return false;
            }

            JsonNode json = objectMapper.readTree(resp.getBody());
            int retCode = json.path("retCode").asInt(-1);

            if (retCode != 0) {
                log.warn("❌ Bybit вернул retCode != 0: {}, msg={}, body={}", retCode, json.path("retMsg").asText(), resp.getBody());
                return false;
            }

            log.info("✅ Успешное подключение к Bybit: retCode=0");
            return true;

        } catch (HttpClientErrorException.Unauthorized ex) {
            log.warn("❌ Bybit: Неверный API ключ или подпись (401): {}", ex.getMessage());
            return false;
        } catch (Exception ex) {
            log.error("❌ Ошибка при подключении к Bybit: {}", ex.getMessage(), ex);
            return false;
        }
    }

    private String hmacSha256(String secret, String message) {
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            byte[] hash = sha256_HMAC.doFinal(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("❌ Ошибка при генерации подписи HMAC-SHA256", e);
        }
    }
}
