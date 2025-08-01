package com.chicu.aibot.exchange.bybit;

import com.chicu.aibot.exchange.client.ExchangeClient;
import com.chicu.aibot.exchange.enums.NetworkType;
import com.chicu.aibot.exchange.model.*;
import com.chicu.aibot.exchange.util.HmacUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component("BYBIT")
@RequiredArgsConstructor
public class BybitExchangeClient implements ExchangeClient {

    @Value("${bybit.api.mainnet-base-url}")
    private String mainnetBaseUrl;

    @Value("${bybit.api.testnet-base-url}")
    private String testnetBaseUrl;

    private final RestTemplate rest;
    private final ObjectMapper objectMapper;

    private String baseUrl(NetworkType network) {
        return (network == NetworkType.MAINNET ? mainnetBaseUrl : testnetBaseUrl)
                .replaceAll("/+$", "");
    }

    private String buildQuery(long ts, String apiKey, String recvWindow) {
        return String.format("accountType=UNIFIED&recvWindow=%s&timestamp=%d", recvWindow, ts);
    }

    private HttpHeaders buildHeaders(String apiKey, String signature, long ts, String recvWindow) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-BAPI-API-KEY", apiKey);
        headers.set("X-BAPI-SIGN", signature);
        headers.set("X-BAPI-TIMESTAMP", String.valueOf(ts));
        headers.set("X-BAPI-RECV-WINDOW", recvWindow);
        return headers;
    }

    @Override
    public boolean testConnection(String apiKey, String secretKey, NetworkType networkType) {
        String endpoint = "/v5/account/wallet-balance";
        String recvWindow = "5000";
        long ts = Instant.now().toEpochMilli();
        String query = buildQuery(ts, apiKey, recvWindow);
        String toSign = ts + apiKey + recvWindow + query;
        String signature = HmacUtil.sha256Hex(secretKey, toSign);

        String url = baseUrl(networkType) + endpoint + "?" + query;
        HttpEntity<Void> request = new HttpEntity<>(buildHeaders(apiKey, signature, ts, recvWindow));
        try {
            ResponseEntity<String> resp = rest.exchange(url, HttpMethod.GET, request, String.class);
            JsonNode root = objectMapper.readTree(resp.getBody());
            return resp.getStatusCode() == HttpStatus.OK && root.path("retCode").asInt() == 0;
        } catch (Exception ex) {
            log.warn("Bybit testConnection failed: {}", ex.getMessage());
            return false;
        }
    }

    @Override
    public AccountInfo fetchAccountInfo(String apiKey, String secretKey, NetworkType networkType) {
        String endpoint = "/v5/account/wallet-balance";
        String recvWindow = "5000";
        long ts = Instant.now().toEpochMilli();
        String query = buildQuery(ts, apiKey, recvWindow);
        String signature = HmacUtil.sha256Hex(secretKey, ts + apiKey + recvWindow + query);

        String url = baseUrl(networkType) + endpoint + "?" + query;
        HttpEntity<Void> request = new HttpEntity<>(buildHeaders(apiKey, signature, ts, recvWindow));
        try {
            JsonNode list = objectMapper
                    .readTree(rest.exchange(url, HttpMethod.GET, request, String.class).getBody())
                    .path("result")
                    .path("list");
            List<Balance> balances = new ArrayList<>();
            for (JsonNode b : list) {
                balances.add(Balance.builder()
                        .asset(b.path("coin").asText())
                        .free(b.path("free").decimalValue())
                        .locked(b.path("locked").decimalValue())
                        .build());
            }
            return AccountInfo.builder().balances(balances).build();
        } catch (Exception ex) {
            log.error("Bybit fetchAccountInfo failed: {}", ex.getMessage(), ex);
            throw new RuntimeException("Failed to fetch Bybit account info", ex);
        }
    }

    @Override
    public OrderResponse placeOrder(String apiKey, String secretKey, NetworkType networkType, OrderRequest req) {
        String endpoint = "/v5/order/create";
        String recvWindow = "5000";
        long ts = Instant.now().toEpochMilli();

        StringBuilder q = new StringBuilder()
                .append("apiKey=").append(apiKey)
                .append("&side=").append(req.getSide())
                .append("&symbol=").append(req.getSymbol())
                .append("&type=").append(req.getType())
                .append("&qty=").append(req.getQuantity())
                .append("&recvWindow=").append(recvWindow)
                .append("&timestamp=").append(ts);
        if (req.getPrice() != null) {
            q.append("&price=").append(req.getPrice());
        }
        String signature = HmacUtil.sha256Hex(secretKey, ts + apiKey + recvWindow + q);
        String url = baseUrl(networkType) + endpoint + "?" + q + "&sign=" + signature;

        try {
            JsonNode result = objectMapper
                    .readTree(rest.postForObject(url, null, String.class))
                    .path("result");
            return OrderResponse.builder()
                    .orderId(result.path("orderId").asText())
                    .symbol(result.path("symbol").asText())
                    .status(result.path("orderStatus").asText())
                    .executedQty(result.path("cumExecQty").decimalValue())
                    .transactTime(Instant.ofEpochMilli(result.path("createTimeMs").asLong()))
                    .build();
        } catch (Exception ex) {
            log.error("Bybit placeOrder failed: {}", ex.getMessage(), ex);
            throw new RuntimeException("Failed to place Bybit order", ex);
        }
    }

    // Вспомогательный метод: получить все тикеры категории spot
    private List<JsonNode> fetchSpotTickers() {
        String url = baseUrl(NetworkType.MAINNET) + "/v5/market/tickers?category=spot";
        try {
            JsonNode list = objectMapper
                    .readTree(rest.getForObject(url, String.class))
                    .path("result")
                    .path("list");
            List<JsonNode> nodes = new ArrayList<>();
            list.forEach(nodes::add);
            return nodes;
        } catch (Exception ex) {
            log.error("Bybit fetchSpotTickers failed: {}", ex.getMessage(), ex);
            return Collections.emptyList();
        }
    }

    @Override
    public List<String> fetchPopularSymbols() {
        // просто все spot-символы
        return fetchSpotTickers().stream()
                .map(n -> n.path("symbol").asText())
                .collect(Collectors.toList());
    }

    @Override
    public List<String> fetchGainers() {
        return fetchSpotTickers().stream()
                // price24hPcnt в формате строка, например "0.1234"
                .sorted(Comparator.comparingDouble(n ->
                        -Double.parseDouble(n.path("price24hPcnt").asText())))
                .map(n -> n.path("symbol").asText())
                .collect(Collectors.toList());
    }

    @Override
    public List<String> fetchLosers() {
        return fetchSpotTickers().stream()
                .sorted(Comparator.comparingDouble(n ->
                        Double.parseDouble(n.path("price24hPcnt").asText())))
                .map(n -> n.path("symbol").asText())
                .collect(Collectors.toList());
    }

    @Override
    public List<String> fetchByVolume() {
        return fetchSpotTickers().stream()
                // turnover24h — объём торгов за 24ч
                .sorted(Comparator.comparingDouble(n ->
                        -Double.parseDouble(n.path("turnover24h").asText())))
                .map(n -> n.path("symbol").asText())
                .collect(Collectors.toList());
    }
    // src/main/java/com/chicu/aibot/exchange/bybit/BybitExchangeClient.java
    @Override
    public TickerInfo getTicker(String symbol, NetworkType networkType) {
        String base       = baseUrl(networkType);
        String endpoint   = "/v5/market/tickers";
        String url        = String.format("%s%s?symbol=%s", base, endpoint, symbol);

        try {
            ResponseEntity<String> resp = rest.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()), String.class);
            JsonNode list = objectMapper.readTree(resp.getBody())
                    .path("result")
                    .path("list");
            if (!list.isArray() || list.isEmpty()) {
                throw new RuntimeException("Empty ticker list for " + symbol);
            }
            JsonNode info = list.get(0);
            BigDecimal price    = new BigDecimal(info.path("lastPrice").asText());
            BigDecimal changePct= new BigDecimal(info.path("price24hP").asText());
            return TickerInfo.builder()
                    .price(price)
                    .changePct(changePct)
                    .build();
        } catch (Exception ex) {
            log.error("❌ Bybit getTicker error for {}: {}", symbol, ex.getMessage(), ex);
            throw new RuntimeException("Failed to fetch ticker for " + symbol, ex);
        }
    }

}
