package com.chicu.aibot.exchange.binance;

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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component("BINANCE")
@RequiredArgsConstructor
public class BinanceExchangeClient implements ExchangeClient {

    @Value("${binance.api.mainnet-base-url}")
    private String mainnetBaseUrl;

    @Value("${binance.api.testnet-base-url}")
    private String testnetBaseUrl;

    private final RestTemplate rest;
    private final ObjectMapper objectMapper;

    private String baseUrl(NetworkType network) {
        return (network == NetworkType.MAINNET ? mainnetBaseUrl : testnetBaseUrl)
                .replaceAll("/+$", "");
    }

    private long currentTimestamp() {
        return Instant.now().toEpochMilli();
    }

    private String sign(String secretKey, String payload) {
        return HmacUtil.sha256Hex(secretKey, payload);
    }

    private HttpHeaders apiKeyHeader(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-MBX-APIKEY", apiKey);
        return headers;
    }

    private ResponseEntity<String> doGet(String url, HttpHeaders headers) {
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private JsonNode parse(String body) throws Exception {
        return objectMapper.readTree(body);
    }

    @Override
    public boolean testConnection(String apiKey, String secretKey, NetworkType networkType) {
        String endpoint = "/api/v3/account";
        long ts = currentTimestamp();
        String query = "timestamp=" + ts;
        String sig = sign(secretKey, query);
        String url = String.format("%s%s?%s&signature=%s", baseUrl(networkType), endpoint, query, sig);

        try {
            ResponseEntity<String> resp = doGet(url, apiKeyHeader(apiKey));
            if (resp.getStatusCode() != HttpStatus.OK || resp.getBody() == null) {
                log.warn("Binance testConnection failed: status={} body={}",
                        resp.getStatusCode(), resp.getBody());
                return false;
            }
            JsonNode root = parse(resp.getBody());
            if (!root.has("balances")) {
                log.warn("Binance testConnection: missing 'balances' field");
                return false;
            }
            log.info("✅ Binance testConnection succeeded for apiKey={}", apiKey);
            return true;
        } catch (Exception ex) {
            log.error("❌ Binance testConnection error: {}", ex.getMessage(), ex);
            return false;
        }
    }

    @Override
    public AccountInfo fetchAccountInfo(String apiKey, String secretKey, NetworkType networkType) {
        String endpoint = "/api/v3/account";
        long ts = currentTimestamp();
        String query = "timestamp=" + ts;
        String sig = sign(secretKey, query);
        String url = String.format("%s%s?%s&signature=%s", baseUrl(networkType), endpoint, query, sig);

        try {
            JsonNode root = parse(doGet(url, apiKeyHeader(apiKey)).getBody());
            List<Balance> balances = new ArrayList<>();
            for (JsonNode b : root.get("balances")) {
                balances.add(Balance.builder()
                        .asset(b.get("asset").asText())
                        .free(b.get("free").decimalValue())
                        .locked(b.get("locked").decimalValue())
                        .build());
            }
            return AccountInfo.builder()
                    .accountId(root.path("accountType").asText(null))
                    .balances(balances)
                    .build();
        } catch (Exception ex) {
            log.error("❌ Binance fetchAccountInfo error: {}", ex.getMessage(), ex);
            throw new RuntimeException("Failed to fetch Binance account info", ex);
        }
    }

    @Override
    public OrderResponse placeOrder(String apiKey, String secretKey, NetworkType networkType, OrderRequest req) {
        String endpoint = "/api/v3/order";
        long ts = currentTimestamp();

        StringBuilder q = new StringBuilder()
                .append("symbol=").append(req.getSymbol())
                .append("&side=").append(req.getSide())
                .append("&type=").append(req.getType())
                .append("&quantity=").append(req.getQuantity());
        if (req.getPrice() != null) {
            q.append("&price=").append(req.getPrice()).append("&timeInForce=GTC");
        }
        q.append("&timestamp=").append(ts);

        String sig = sign(secretKey, q.toString());
        String url = String.format("%s%s?%s&signature=%s", baseUrl(networkType), endpoint, q, sig);

        try {
            JsonNode root = parse(doGet(url, apiKeyHeader(apiKey)).getBody());
            return OrderResponse.builder()
                    .orderId(root.get("orderId").asText())
                    .symbol(root.get("symbol").asText())
                    .status(root.get("status").asText())
                    .executedQty(root.get("executedQty").decimalValue())
                    .transactTime(Instant.ofEpochMilli(root.get("transactTime").asLong()))
                    .build();
        } catch (Exception ex) {
            log.error("❌ Binance placeOrder error: {}", ex.getMessage(), ex);
            throw new RuntimeException("Failed to place Binance order", ex);
        }
    }

    /**
     * Вспомогательный метод: получить все 24h-тикеры с Binance
     */
    private List<JsonNode> fetchTicker24hr() {
        String url = baseUrl(NetworkType.MAINNET) + "/api/v3/ticker/24hr";
        try {
            JsonNode arr = objectMapper.readTree(rest.getForObject(url, String.class));
            List<JsonNode> list = new ArrayList<>();
            arr.forEach(list::add);
            return list;
        } catch (Exception ex) {
            log.error("Binance fetchTicker24hr failed: {}", ex.getMessage(), ex);
            return Collections.emptyList();
        }
    }

    @Override
    public List<String> fetchPopularSymbols() {
        // сортируем по количеству сделок (count) по убыванию
        return fetchTicker24hr().stream()
                .sorted(Comparator.comparingLong(
                        (JsonNode n) -> n.path("count").asLong()
                ).reversed())
                .map(n -> n.path("symbol").asText())
                .collect(Collectors.toList());
    }

    @Override
    public List<String> fetchGainers() {
        // сортируем по проценту изменения (priceChangePercent) по убыванию
        return fetchTicker24hr().stream()
                .sorted(Comparator.comparingDouble(
                        (JsonNode n) -> n.path("priceChangePercent").asDouble()
                ).reversed())
                .map(n -> n.path("symbol").asText())
                .collect(Collectors.toList());
    }

    @Override
    public List<String> fetchLosers() {
        // сортируем по проценту изменения по возрастанию
        return fetchTicker24hr().stream()
                .sorted(Comparator.comparingDouble(
                        (JsonNode n) -> n.path("priceChangePercent").asDouble()
                ))
                .map(n -> n.path("symbol").asText())
                .collect(Collectors.toList());
    }

    @Override
    public List<String> fetchByVolume() {
        // сортируем по объёму торгов (volume) по убыванию
        return fetchTicker24hr().stream()
                .sorted(Comparator.comparingDouble(
                        (JsonNode n) -> n.path("volume").asDouble()
                ).reversed())
                .map(n -> n.path("symbol").asText())
                .collect(Collectors.toList());
    }
    // внутри BinanceExchangeClient

    @Override
    public TickerInfo getTicker(String symbol, NetworkType networkType) {
        String endpoint = "/api/v3/ticker/24hr";
        String url = String.format("%s%s?symbol=%s",
                baseUrl(networkType),
                endpoint,
                symbol);

        try {
            ResponseEntity<String> resp = rest.exchange(
                    url, HttpMethod.GET, HttpEntity.EMPTY, String.class);
            JsonNode node = objectMapper.readTree(resp.getBody());
            BigDecimal price     = new BigDecimal(node.get("lastPrice").asText());
            BigDecimal changePct = new BigDecimal(node.get("priceChangePercent").asText());
            return TickerInfo.builder()
                    .price(price)
                    .changePct(changePct)
                    .build();
        } catch (HttpClientErrorException.BadRequest ex) {
            log.warn("Пропускаем несуществующий на {} символ {}: {}",
                    networkType, symbol, ex.getResponseBodyAsString());
            return null;  // <-- не кидаем, а возвращаем null
        } catch (Exception ex) {
            log.error("Ошибка getTicker для {}: {}", symbol, ex.getMessage());
            return null;
        }
    }

}
