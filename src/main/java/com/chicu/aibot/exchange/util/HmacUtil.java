package com.chicu.aibot.exchange.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class HmacUtil {

    /**
     * Генерация HMAC с заданным алгоритмом и возврат hex-строки (в нижнем регистре).
     *
     * @param secret   секретный ключ
     * @param message  исходное сообщение (origin_string)
     * @param algorithm алгоритм, например: HmacSHA256, HmacSHA512
     * @return hex-строка (нижний регистр)
     */
    public static String hmacHex(String secret, String message, String algorithm) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm));
            byte[] hmacBytes = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder(hmacBytes.length * 2);
            for (byte b : hmacBytes) {
                hex.append(String.format("%02x", b & 0xff));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("❌ Ошибка HMAC-HEX (" + algorithm + "): " + e.getMessage(), e);
        }
    }

    /**
     * Генерация HMAC с заданным алгоритмом и возврат строки в Base64.
     *
     * @param secret   секретный ключ
     * @param message  исходное сообщение
     * @param algorithm алгоритм HMAC
     * @return base64-строка
     */
    public static String hmacBase64(String secret, String message, String algorithm) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm));
            byte[] hmacBytes = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacBytes);
        } catch (Exception e) {
            throw new IllegalStateException("❌ Ошибка HMAC-Base64 (" + algorithm + "): " + e.getMessage(), e);
        }
    }

    // Упрощённый метод по умолчанию для большинства бирж
    public static String sha256Hex(String secret, String message) {
        return hmacHex(secret, message, "HmacSHA256");
    }

    public static String sha512Hex(String secret, String message) {
        return hmacHex(secret, message, "HmacSHA512");
    }
}
