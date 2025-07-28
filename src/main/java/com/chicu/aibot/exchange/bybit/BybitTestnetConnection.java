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

public class BybitTestnetConnection {

    public static void main(String[] args) throws Exception {
        // ✅ Testnet API-ключи (должны быть активны и иметь разрешение к счету)
        String apiKey = "lAQvqEGN6FjJUtSDzr";
        String secretKey = "CMnYkZRAsEiCeibGEyx3ZnkNqzipKbScCmKx";

        // ✅ Конфигурация
        String baseUrl = "https://api-testnet.bybit.com";
        String path = "/v5/account/wallet-balance";
        String recvWindow = "5000";
        String accountType = "UNIFIED";
        long timestamp = System.currentTimeMillis();

        // ✅ Query string (в URL и для подписи)
        String queryString = "accountType=" + accountType +
                "&recvWindow=" + recvWindow +
                "&timestamp=" + timestamp;

        // ✅ Правильная строка для подписи:
        String stringToSign = timestamp + apiKey + recvWindow + queryString;

        // ✅ Подпись HMAC SHA256
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256_HMAC.init(secret_key);
        byte[] hash = sha256_HMAC.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        String signature = bytesToHex(hash);

        // ✅ Полный URL запроса
        String url = baseUrl + path + "?" + queryString;

        // ✅ Отправка запроса
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-BAPI-API-KEY", apiKey);
        conn.setRequestProperty("X-BAPI-SIGN", signature);
        conn.setRequestProperty("X-BAPI-TIMESTAMP", String.valueOf(timestamp));
        conn.setRequestProperty("X-BAPI-RECV-WINDOW", recvWindow);

        // ✅ Ответ
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

    // ✅ Перевод массива байтов в hex-строку
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
