package com.chicu.aibot.exchange.bybit;

import com.chicu.aibot.exchange.client.ExchangeClient;
import com.chicu.aibot.exchange.enums.NetworkType;
import com.chicu.aibot.exchange.enums.OrderSide;
import com.chicu.aibot.exchange.model.*;
import com.chicu.aibot.exchange.util.HmacUtil;
import com.chicu.aibot.strategy.model.Candle;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component("BYBIT")
@RequiredArgsConstructor
public class BybitExchangeClient implements ExchangeClient {

    @Value("${bybit.api.mainnet-base-url:https://api.bybit.com}")
    private String mainnetBaseUrl;

    @Value("${bybit.api.testnet-base-url:https://api-testnet.bybit.com}")
    private String testnetBaseUrl;

    private static final String RECV_WINDOW = "5000";

    private final RestTemplate rest;
    private final ObjectMapper objectMapper;

    /* ====================== helpers ====================== */

    private String baseUrl(NetworkType network) {
        String b = (network == NetworkType.MAINNET ? mainnetBaseUrl : testnetBaseUrl);
        return b.replaceAll("/+$", "");
    }

    private static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    private JsonNode parseJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new RuntimeException("JSON parse error: " + e.getMessage(), e);
        }
    }

    /** Общая сборка заголовков для подписанных запросов (payload = queryString для GET или bodyJson для POST). */
    private HttpHeaders signedHeaders(String apiKey, String secretKey, long ts, String payload) {
        String preSign = ts + apiKey + RECV_WINDOW + (payload == null ? "" : payload);
        String sign = HmacUtil.sha256Hex(secretKey, preSign);

        HttpHeaders h = new HttpHeaders();
        h.set("X-BAPI-API-KEY", apiKey);
        h.set("X-BAPI-TIMESTAMP", String.valueOf(ts));
        h.set("X-BAPI-RECV-WINDOW", RECV_WINDOW);
        h.set("X-BAPI-SIGN", sign);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    /** Подписанный GET (payload = queryString). Ключи берутся из thread-local. */
    private JsonNode signedGet(String fullUrl) {
        int i = fullUrl.indexOf('?');
        String query = (i >= 0 && i < fullUrl.length() - 1) ? fullUrl.substring(i + 1) : "";
        long ts = System.currentTimeMillis();

        HttpHeaders headers = signedHeaders(
                currentApiKey.get(),
                currentSecretKey.get(),
                ts,
                query
        );

        ResponseEntity<String> r = rest.exchange(fullUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        return parseJson(r.getBody());
    }

    /** Подписанный POST (payload = JSON-строка). */
    private JsonNode signedPost(String fullUrl, Map<String, Object> body, String apiKey, String secretKey) {
        try {
            String bodyJson = objectMapper.writeValueAsString(body);
            long ts = System.currentTimeMillis();
            HttpHeaders headers = signedHeaders(apiKey, secretKey, ts, bodyJson);
            ResponseEntity<String> r = rest.exchange(fullUrl, HttpMethod.POST, new HttpEntity<>(bodyJson, headers), String.class);
            return parseJson(r.getBody());
        } catch (Exception ex) {
            log.error("❌ Bybit signed POST failed: {}", ex.getMessage());
            throw new RuntimeException("Bybit signed request failed", ex);
        }
    }

    /* ====================== symbol filters cache ====================== */

    private record SymbolFilters(
            BigDecimal tickSize,
            BigDecimal qtyStep,
            BigDecimal minOrderQty
    ) {}

    private final ConcurrentMap<String, SymbolFilters> filtersCache = new ConcurrentHashMap<>();

    private String fKey(NetworkType n, String symbol) {
        return n.name() + ":" + symbol;
    }

    private SymbolFilters getFilters(NetworkType network, String symbol) {
        return filtersCache.computeIfAbsent(fKey(network, symbol), k -> fetchFilters(network, symbol));
    }

    private SymbolFilters fetchFilters(NetworkType network, String symbol) {
        try {
            String url = baseUrl(network) + "/v5/market/instruments-info?category=spot&symbol=" + enc(symbol);
            JsonNode list = parseJson(rest.getForObject(url, String.class))
                    .path("result").path("list");
            if (!list.isArray() || list.isEmpty()) {
                throw new IllegalStateException("Empty instruments-info for " + symbol);
            }
            JsonNode info = list.get(0);
            BigDecimal tickSize    = new BigDecimal(info.path("priceFilter").path("tickSize").asText("0"));
            BigDecimal qtyStep     = new BigDecimal(info.path("lotSizeFilter").path("qtyStep").asText("0"));
            BigDecimal minOrderQty = new BigDecimal(info.path("lotSizeFilter").path("minOrderQty").asText("0"));
            return new SymbolFilters(tickSize, qtyStep, minOrderQty);
        } catch (Exception e) {
            log.warn("Bybit instruments-info fetch failed for {}: {}", symbol, e.getMessage());
            return new SymbolFilters(null, null, null);
        }
    }

    private static BigDecimal quantize(BigDecimal value, BigDecimal step) {
        if (value == null || step == null || step.signum() == 0) return value;
        BigDecimal[] div = value.divideAndRemainder(step);
        BigDecimal floored = div[0].multiply(step);
        int scale = Math.max(0, step.stripTrailingZeros().scale());
        return floored.setScale(scale, RoundingMode.DOWN).stripTrailingZeros();
    }

    /* ====================== Thread-local API keys (для signedGet) ====================== */
    private final ThreadLocal<String> currentApiKey    = new ThreadLocal<>();
    private final ThreadLocal<String> currentSecretKey = new ThreadLocal<>();

    /* ====================== ExchangeClient ====================== */

    @Override
    public boolean testConnection(String apiKey, String secretKey, NetworkType networkType) {
        try {
            String url = baseUrl(networkType) + "/v5/account/wallet-balance?accountType=UNIFIED";
            currentApiKey.set(apiKey);
            currentSecretKey.set(secretKey);
            try {
                JsonNode root = signedGet(url);
                return root.path("retCode").asInt(-1) == 0;
            } finally {
                currentApiKey.remove();
                currentSecretKey.remove();
            }
        } catch (Exception e) {
            log.warn("Bybit testConnection failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public AccountInfo fetchAccountInfo(String apiKey, String secretKey, NetworkType networkType) {
        try {
            String url = baseUrl(networkType) + "/v5/account/wallet-balance?accountType=UNIFIED";
            currentApiKey.set(apiKey);
            currentSecretKey.set(secretKey);
            try {
                JsonNode list = signedGet(url).path("result").path("list");
                List<Balance> balances = new ArrayList<>();
                for (JsonNode acc : list) {
                    for (JsonNode c : acc.path("coin")) {
                        balances.add(Balance.builder()
                                .asset(c.path("coin").asText())
                                .free(new BigDecimal(c.path("availableToWithdraw").asText("0")))
                                .locked(new BigDecimal(c.path("locked").asText("0")))
                                .build());
                    }
                }
                return AccountInfo.builder().balances(balances).build();
            } finally {
                currentApiKey.remove();
                currentSecretKey.remove();
            }
        } catch (Exception ex) {
            log.error("Bybit fetchAccountInfo failed: {}", ex.getMessage(), ex);
            throw new RuntimeException("Failed to fetch Bybit account info", ex);
        }
    }

    @Override
    public OrderResponse placeOrder(String apiKey, String secretKey, NetworkType network, OrderRequest req) {
        try {
            SymbolFilters f = getFilters(network, req.getSymbol());
            BigDecimal qtyNorm = quantize(req.getQuantity(), f.qtyStep());
            if (qtyNorm == null || qtyNorm.signum() == 0) {
                throw new IllegalArgumentException("Quantity is zero after quantize");
            }

            // Bybit чувствителен к регистру
            String orderType = switch (req.getType()) {
                case LIMIT  -> "Limit";
                case MARKET -> "Market";
            };

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("category", "spot");
            body.put("symbol", req.getSymbol());
            body.put("side", req.getSide().name().equals("BUY") ? "Buy" : "Sell");
            body.put("orderType", orderType);
            body.put("qty", qtyNorm.stripTrailingZeros().toPlainString());

            if ("Limit".equals(orderType)) {
                BigDecimal priceNorm = quantize(req.getPrice(), f.tickSize());
                if (priceNorm == null || priceNorm.signum() == 0) {
                    throw new IllegalArgumentException("Price is zero after quantize");
                }
                body.put("price", priceNorm.stripTrailingZeros().toPlainString());
                body.put("timeInForce", "GTC");
            }

            String url = baseUrl(network) + "/v5/order/create";

            JsonNode root = signedPost(url, body, apiKey, secretKey);
            int retCode = root.path("retCode").asInt(-1);
            if (retCode != 0) {
                String retMsg = root.path("retMsg").asText();
                String details = root.path("result").toString();
                throw new RuntimeException("Bybit create order failed: retCode=" + retCode + ", retMsg=" + retMsg + ", result=" + details);
            }

            JsonNode result = root.path("result");
            String orderId = result.path("orderId").asText(null); // тут уже должен прийти
            // статус при создании часто не возвращают — считаем NEW; уточним через getOrder/refresh
            String status = result.hasNonNull("orderStatus")
                    ? result.path("orderStatus").asText()
                    : "NEW";

            BigDecimal executed = BigDecimal.ZERO;
            if (result.hasNonNull("cumExecQty")) {
                executed = new BigDecimal(result.path("cumExecQty").asText("0"));
            }

            long ts = result.hasNonNull("createTime")
                    ? result.path("createTime").asLong()
                    : System.currentTimeMillis();

            return OrderResponse.builder()
                    .orderId(orderId)
                    .symbol(result.path("symbol").asText(req.getSymbol()))
                    .status(status)
                    .executedQty(executed)
                    .transactTime(Instant.ofEpochMilli(ts))
                    .build();

        } catch (Exception ex) {
            log.error("❌ Bybit placeOrder failed: {}", ex.getMessage(), ex);
            throw new RuntimeException("Failed to place Bybit order", ex);
        }
    }

    /* ===== NEW: отмена ордера ===== */
    @Override
    public boolean cancelOrder(String apiKey, String secretKey, NetworkType network, String symbol, String orderId) {
        try {
            Map<String,Object> body = new LinkedHashMap<>();
            body.put("category", "spot");
            body.put("symbol", symbol);
            body.put("orderId", orderId);

            String url = baseUrl(network) + "/v5/order/cancel";
            JsonNode root = signedPost(url, body, apiKey, secretKey);
            int ret = root.path("retCode").asInt(-1);
            if (ret != 0) {
                log.warn("Bybit cancelOrder retCode={}, msg={}", ret, root.path("retMsg").asText());
            }
            return ret == 0;
        } catch (Exception ex) {
            log.error("❌ Bybit cancelOrder failed: {}", ex.getMessage(), ex);
            return false;
        }
    }

    /* ===== NEW: открытые ордера ===== */
    @Override
    public List<OrderInfo> fetchOpenOrders(String apiKey, String secretKey, NetworkType network, String symbol) {
        List<OrderInfo> out = new ArrayList<>();
        String url = baseUrl(network) + "/v5/order/realtime?category=spot"
                + (symbol != null && !symbol.isBlank() ? "&symbol=" + enc(symbol) : "");

        currentApiKey.set(apiKey);
        currentSecretKey.set(secretKey);
        try {
            JsonNode list = signedGet(url).path("result").path("list");
            if (list.isArray()) {
                for (JsonNode n : list) out.add(toInfo(n));
            }
        } catch (Exception ex) {
            log.warn("Bybit fetchOpenOrders error: {}", ex.getMessage());
        } finally {
            currentApiKey.remove();
            currentSecretKey.remove();
        }
        return out;
    }

    /* ===== NEW: статус конкретного ордера (realtime -> history fallback) ===== */
    @Override
    public Optional<OrderInfo> fetchOrder(String apiKey, String secretKey, NetworkType network,
                                                  String symbol, String orderId) {
        // 1) пробуем realtime
        String rt = baseUrl(network) + "/v5/order/realtime?category=spot"
                + "&symbol=" + enc(symbol) + "&orderId=" + enc(orderId);

        currentApiKey.set(apiKey);
        currentSecretKey.set(secretKey);
        try {
            JsonNode list = signedGet(rt).path("result").path("list");
            if (list.isArray() && !list.isEmpty()) {
                return Optional.of(toInfo(list.get(0)));
            }
        } catch (Exception ex) {
            log.debug("Bybit fetchOrder realtime miss: {}", ex.getMessage());
        } finally {
            currentApiKey.remove();
            currentSecretKey.remove();
        }

        // 2) если в открытых нет — берём историю
        String hist = baseUrl(network) + "/v5/order/history?category=spot"
                + "&symbol=" + enc(symbol) + "&orderId=" + enc(orderId);

        currentApiKey.set(apiKey);
        currentSecretKey.set(secretKey);
        try {
            JsonNode list = signedGet(hist).path("result").path("list");
            if (list.isArray() && !list.isEmpty()) {
                return Optional.of(toInfo(list.get(0)));
            }
        } catch (Exception ex) {
            log.warn("Bybit fetchOrder history error: {}", ex.getMessage());
        } finally {
            currentApiKey.remove();
            currentSecretKey.remove();
        }

        return Optional.empty();
    }

    private OrderInfo toInfo(JsonNode n) {
        String status = n.path("orderStatus").asText("");           // New / PartiallyFilled / Filled / Cancelled ...
        String sideStr = n.path("side").asText("Buy");
        OrderSide side = "Buy".equalsIgnoreCase(sideStr) ? OrderSide.BUY : OrderSide.SELL;

        BigDecimal price = BigDecimal.ZERO;
        if (n.hasNonNull("price")) {
            String p = n.path("price").asText("0");
            if (!"".equals(p)) price = new BigDecimal(p);
        }
        BigDecimal exec = BigDecimal.ZERO;
        if (n.hasNonNull("cumExecQty")) {
            String q = n.path("cumExecQty").asText("0");
            if (!"".equals(q)) exec = new BigDecimal(q);
        }

        return OrderInfo.builder()
                .orderId(n.path("orderId").asText(null))
                .symbol(n.path("symbol").asText(null))
                .side(side)
                .status(status.toUpperCase(Locale.ROOT)) // нормализуем под общий стиль
                .price(price)
                .executedQty(exec)
                .build();
    }

    // ===== tickers (mainnet) =====

    private List<JsonNode> fetchSpotTickers() {
        try {
            String url = baseUrl(NetworkType.MAINNET) + "/v5/market/tickers?category=spot";
            JsonNode list = parseJson(rest.getForObject(url, String.class)).path("result").path("list");
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
        return fetchSpotTickers().stream()
                .map(n -> n.path("symbol").asText())
                .toList();
    }

    @Override
    public List<String> fetchGainers() {
        return fetchSpotTickers().stream()
                .sorted(Comparator.comparingDouble(n ->
                        -Double.parseDouble(n.path("price24hPcnt").asText("0"))))
                .map(n -> n.path("symbol").asText())
                .toList();
    }

    @Override
    public List<String> fetchLosers() {
        return fetchSpotTickers().stream()
                .sorted(Comparator.comparingDouble(n ->
                        Double.parseDouble(n.path("price24hPcnt").asText("0"))))
                .map(n -> n.path("symbol").asText())
                .toList();
    }

    @Override
    public List<String> fetchByVolume() {
        return fetchSpotTickers().stream()
                .sorted(Comparator.comparingDouble(n ->
                        -Double.parseDouble(n.path("turnover24h").asText("0"))))
                .map(n -> n.path("symbol").asText())
                .toList();
    }

    @Override
    public TickerInfo getTicker(String symbol, NetworkType networkType) {
        try {
            String url = baseUrl(networkType) + "/v5/market/tickers?category=spot&symbol=" + enc(symbol);
            ResponseEntity<String> resp = rest.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()), String.class);
            JsonNode list = parseJson(resp.getBody()).path("result").path("list");
            if (!list.isArray() || list.isEmpty()) {
                throw new RuntimeException("Empty ticker list for " + symbol);
            }
            JsonNode info = list.get(0);
            BigDecimal price     = new BigDecimal(info.path("lastPrice").asText("0"));
            BigDecimal changePct = new BigDecimal(info.path("price24hPcnt").asText("0"));
            return TickerInfo.builder()
                    .price(price)
                    .changePct(changePct)
                    .build();
        } catch (Exception ex) {
            log.error("❌ Bybit getTicker error for {}: {}", symbol, ex.getMessage(), ex);
            throw new RuntimeException("Failed to fetch ticker for " + symbol, ex);
        }
    }

    @Override
    public List<Candle> fetchCandles(String apiKey, String secretKey, NetworkType network,
                                     String symbol, String interval, int limit) {
        try {
            String bybitInterval = mapInterval(interval); // "1m"->"1", "1h"->"60", "1d"->"D"
            String url = baseUrl(network) + "/v5/market/kline?category=spot"
                    + "&symbol=" + enc(symbol)
                    + "&interval=" + enc(bybitInterval)
                    + "&limit=" + limit;

            JsonNode list = parseJson(rest.getForObject(url, String.class))
                    .path("result").path("list");
            List<Candle> candles = new ArrayList<>();
            for (JsonNode n : list) {
                Instant openTime = Instant.ofEpochMilli(n.get(0).asLong());
                BigDecimal open  = new BigDecimal(n.get(1).asText("0"));
                BigDecimal high  = new BigDecimal(n.get(2).asText("0"));
                BigDecimal low   = new BigDecimal(n.get(3).asText("0"));
                BigDecimal close = new BigDecimal(n.get(4).asText("0"));
                BigDecimal vol   = new BigDecimal(n.get(5).asText("0"));
                candles.add(new Candle(symbol, openTime, open, high, low, close, vol));
            }
            candles.sort(Comparator.comparing(Candle::getOpenTime));
            return candles;
        } catch (Exception ex) {
            log.error("Ошибка Bybit fetchCandles для {} {}: {}", symbol, interval, ex.getMessage(), ex);
            return Collections.emptyList();
        }
    }

    /* ====================== timeframe map ====================== */

    private String mapInterval(String tfRaw) {
        if (tfRaw == null || tfRaw.isBlank()) return "1";
        String tf = tfRaw.trim().toLowerCase();
        if (tf.endsWith("m")) {
            return tf.substring(0, tf.length() - 1); // "1m"->"1", "15m"->"15"
        }
        if (tf.endsWith("h")) {
            long h = Long.parseLong(tf.substring(0, tf.length() - 1));
            return String.valueOf(h * 60); // "1h"->"60", "4h"->"240"
        }
        if (tf.endsWith("d")) return "D";
        if (tf.endsWith("w")) return "W";
        if (tf.endsWith("mo") || tf.endsWith("mon") || tf.endsWith("month")) return "M";
        return tf;
    }

    /** Преобразование ответа Bybit в наш OrderInfo */
    private OrderInfo toOrderInfo(JsonNode n) {
        BigDecimal executed = BigDecimal.ZERO;
        if (n.hasNonNull("cumExecQty")) {
            String q = n.path("cumExecQty").asText("0");
            if (!q.isEmpty()) executed = new BigDecimal(q);
        }
        return OrderInfo.builder()
                .orderId(n.path("orderId").asText(null))
                .symbol(n.path("symbol").asText(null))
                .status(n.path("orderStatus").asText("").toUpperCase(Locale.ROOT)) // New->NEW, Filled->FILLED...
                .executedQty(executed)
                .build();
    }

    /** Реализация абстрактного метода интерфейса: статус конкретного ордера */
    @Override
    public OrderInfo getOrder(String apiKey, String secretKey,
                              NetworkType networkType, String symbol, String orderId) {
        // 1) пробуем "realtime" (открытые/активные)
        String rt = baseUrl(networkType) + "/v5/order/realtime?category=spot"
                + "&symbol=" + enc(symbol) + "&orderId=" + enc(orderId);

        currentApiKey.set(apiKey);
        currentSecretKey.set(secretKey);
        try {
            JsonNode list = signedGet(rt).path("result").path("list");
            if (list.isArray() && !list.isEmpty()) {
                return toOrderInfo(list.get(0));
            }
        } catch (Exception ignore) {
            // пойдём в историю
        } finally {
            currentApiKey.remove();
            currentSecretKey.remove();
        }

        // 2) если в realtime нет — смотрим историю
        String hist = baseUrl(networkType) + "/v5/order/history?category=spot"
                + "&symbol=" + enc(symbol) + "&orderId=" + enc(orderId);

        currentApiKey.set(apiKey);
        currentSecretKey.set(secretKey);
        try {
            JsonNode list = signedGet(hist).path("result").path("list");
            if (list.isArray() && !list.isEmpty()) {
                return toOrderInfo(list.get(0));
            }
        } catch (Exception e) {
            log.warn("Bybit getOrder history error: {}", e.getMessage());
        } finally {
            currentApiKey.remove();
            currentSecretKey.remove();
        }

        return null; // не нашли
    }

    /** Открытые ордера по символу — под сигнатуру интерфейса */
    @Override
    public List<OrderInfo> getOpenOrders(String apiKey, String secretKey,
                                         NetworkType networkType, String symbol) {
        String url = baseUrl(networkType) + "/v5/order/realtime?category=spot"
                + (symbol != null && !symbol.isBlank() ? "&symbol=" + enc(symbol) : "");

        currentApiKey.set(apiKey);
        currentSecretKey.set(secretKey);
        try {
            JsonNode list = signedGet(url).path("result").path("list");
            List<OrderInfo> out = new ArrayList<>();
            if (list.isArray()) {
                for (JsonNode n : list) out.add(toOrderInfo(n));
            }
            return out;
        } catch (Exception e) {
            log.warn("Bybit getOpenOrders error: {}", e.getMessage());
            return List.of();
        } finally {
            currentApiKey.remove();
            currentSecretKey.remove();
        }
    }
}
