package com.chicu.aibot.exchange.bybit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class BybitTestConnection {

    public static void main(String[] args) throws Exception {
        // === API-ключи ===
        String apiKey = "V9XyS5N6StUkb1empJ";
        String secretKey = "qPFURSu0FF1TO1x9ssUHwHbZXBUBfzqVxN6Y";

        // === Конфигурация запроса ===
        String baseUrl = "https://api.bybit.com"; // real
        String path = "/v5/account/wallet-balance";
        long timestamp = System.currentTimeMillis();
        String recvWindow = "5000";
        String accountType = "UNIFIED";

        // === queryString ===
        String queryString = "accountType=" + accountType +
                "&recvWindow=" + recvWindow +
                "&timestamp=" + timestamp;

        // === ВАЖНО: Строка для подписи ===
        String stringToSign = timestamp + apiKey + recvWindow + queryString;

        // === Подпись HMAC SHA256 ===
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256_HMAC.init(secret_key);
        byte[] hash = sha256_HMAC.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        String signature = bytesToHex(hash);

        // === Полный URL ===
        String url = baseUrl + path + "?" + queryString;

        // === Запрос ===
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-BAPI-API-KEY", apiKey);
        conn.setRequestProperty("X-BAPI-SIGN", signature);
        conn.setRequestProperty("X-BAPI-TIMESTAMP", String.valueOf(timestamp));
        conn.setRequestProperty("X-BAPI-RECV-WINDOW", recvWindow);

        // === Ответ ===
        int responseCode = conn.getResponseCode();
        InputStream stream = responseCode == 200 ? conn.getInputStream()
                : (conn.getErrorStream() != null ? conn.getErrorStream() : new ByteArrayInputStream("".getBytes()));

        BufferedReader in = new BufferedReader(new InputStreamReader(stream));
        String inputLine;
        StringBuilder response = new StringBuilder();
        while ((inputLine = in.readLine()) != null) response.append(inputLine);
        in.close();

        System.out.println("Response Code: " + responseCode);
        System.out.println("Response: " + response);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
