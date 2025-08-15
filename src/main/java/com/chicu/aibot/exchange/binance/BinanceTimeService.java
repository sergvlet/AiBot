package com.chicu.aibot.exchange.binance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class BinanceTimeService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final long TIME_SYNC_EVERY_MS = 60_000; // 1 минута
    private volatile long timeOffsetMs = 0L;               // server - local
    private volatile long lastSyncAt = 0L;

    public long currentTimestampMs() {
        return System.currentTimeMillis() + timeOffsetMs;
    }

    public void ensureSynced(String baseUrl) {
        long now = System.currentTimeMillis();
        if (now - lastSyncAt > TIME_SYNC_EVERY_MS) {
            sync(baseUrl);
        }
    }

    public void forceResync(String baseUrl) {
        sync(baseUrl);
    }

    private synchronized void sync(String baseUrl) {
        try {
            long t0 = System.currentTimeMillis();
            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl + "/api/v3/time", HttpMethod.GET, new HttpEntity<>(null), String.class);
            long t1 = System.currentTimeMillis();

            JsonNode node = objectMapper.readTree(resp.getBody());
            long serverTime = node.get("serverTime").asLong();
            long localMid = (t0 + t1) / 2L;

            timeOffsetMs = serverTime - localMid;
            lastSyncAt = System.currentTimeMillis();

            log.info("Binance time sync: offset={} ms (server={}, localMid={})",
                    timeOffsetMs, serverTime, localMid);
        } catch (Exception e) {
            log.warn("Binance time sync failed: {}", e.getMessage());
        }
    }
}
