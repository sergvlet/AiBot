package com.chicu.aibot.exchange.binance;

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
import org.springframework.web.client.HttpClientErrorException;
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
@Component("BINANCE")
@RequiredArgsConstructor
public class BinanceExchangeClient implements ExchangeClient {

    @Value("${binance.api.mainnet-base-url:https://api.binance.com}")
    private String mainnetBaseUrl;

    @Value("${binance.api.testnet-base-url:https://testnet.binance.vision}")
    private String testnetBaseUrl;

    private static final String RECV_WINDOW = "5000";
    private static final long TIME_SYNC_PERIOD_MS = 60_000L; // 1 минута
    private volatile long timeOffsetMs = 0L;                  // server - local(mid)
    private volatile long lastSyncAtMs = 0L;

    private final RestTemplate rest;
    private final ObjectMapper objectMapper;

    /* ========== helpers ========== */

    private String baseUrl(NetworkType network) {
        String b = (network == NetworkType.MAINNET ? mainnetBaseUrl : testnetBaseUrl);
        if (b == null || b.isBlank()) b = "https://api.binance.com";
        if (!b.startsWith("http")) b = "https://" + b;
        return b.replaceAll("/+$", "");
    }

    private static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    private HttpHeaders apiKeyHeader(String apiKey) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-MBX-APIKEY", apiKey);
        h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return h;
    }

    private String sign(String secretKey, String query) {
        return HmacUtil.sha256Hex(secretKey, query);
    }

    private JsonNode parseJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new RuntimeException("JSON parse error: " + e.getMessage(), e);
        }
    }

    private ResponseEntity<String> doGet(String url, HttpHeaders headers) {
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> doPost(String url, HttpHeaders headers) {
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>((String) null, headers), String.class);
    }

    private ResponseEntity<String> doDelete(String url, HttpHeaders headers) {
        return rest.exchange(url, HttpMethod.DELETE, new HttpEntity<>((String) null, headers), String.class);
    }

    /* ==== time sync ==== */

    private void ensureTimeSynced(NetworkType n) {
        long now = System.currentTimeMillis();
        if (now - lastSyncAtMs > TIME_SYNC_PERIOD_MS) {
            syncTime(n);
        }
    }

    private synchronized void syncTime(NetworkType n) {
        try {
            String url = baseUrl(n) + "/api/v3/time";
            long t0 = System.currentTimeMillis();
            ResponseEntity<String> r = rest.getForEntity(url, String.class);
            long t1 = System.currentTimeMillis();

            long serverTime = parseJson(r.getBody()).path("serverTime").asLong();
            long localMid = (t0 + t1) / 2L;
            timeOffsetMs = serverTime - localMid;
            lastSyncAtMs = System.currentTimeMillis();

            log.info("Binance time sync: offset={} ms (server={}, localMid={})", timeOffsetMs, serverTime, localMid);
        } catch (Exception e) {
            log.warn("Binance time sync failed: {}", e.getMessage());
        }
    }

    private long nowMs(NetworkType n) {
        ensureTimeSynced(n);
        return System.currentTimeMillis() + timeOffsetMs;
    }

    private static boolean isTimestampError(Throwable e) {
        if (e instanceof HttpClientErrorException he) {
            String body = he.getResponseBodyAsString();
            return body.contains("\"code\":-1021");
        }
        return false;
    }

    /** Универсальный вызов подписанного эндпоинта с авто-ретраем при -1021. */
    private String signedRequest(
            NetworkType n,
            String path,
            String partialQuery,       // без recvWindow/timestamp/signature
            String apiKey,
            String secretKey,
            HttpMethod method
    ) {
        String base = baseUrl(n) + path;
        String body;
        long ts = nowMs(n);

        String q1 = buildQuery(partialQuery, ts);
        String sig1 = sign(secretKey, q1);
        String url1 = base + "?" + q1 + "&signature=" + sig1;

        try {
            body = exchange(method, url1, apiKeyHeader(apiKey)).getBody();
            return body;
        } catch (HttpClientErrorException e) {
            if (!isTimestampError(e)) throw e;
            log.warn("{} {} -> -1021 (timestamp), resync and retry once", method, path);
            syncTime(n);
            long ts2 = nowMs(n);
            String q2 = buildQuery(partialQuery, ts2);
            String sig2 = sign(secretKey, q2);
            String url2 = base + "?" + q2 + "&signature=" + sig2;
            return exchange(method, url2, apiKeyHeader(apiKey)).getBody();
        }
    }

    private String buildQuery(String partialQuery, long timestamp) {
        String base = (partialQuery == null || partialQuery.isBlank()) ? "" : partialQuery;
        StringBuilder sb = new StringBuilder();
        if (!base.isBlank()) sb.append(base).append("&");
        sb.append("recvWindow=").append(RECV_WINDOW)
                .append("&timestamp=").append(timestamp);
        return sb.toString();
    }

    private ResponseEntity<String> exchange(HttpMethod m, String url, HttpHeaders headers) {
        if (m == HttpMethod.GET) {
            return doGet(url, headers);
        } else if (m == HttpMethod.POST) {
            return doPost(url, headers);
        } else if (m == HttpMethod.DELETE) {
            return doDelete(url, headers);
        } else {
            throw new IllegalArgumentException("Unsupported HTTP method: " + m);
        }
    }

    private String signedGet(NetworkType n, String path, String partialQuery, String apiKey, String secretKey) {
        return signedRequest(n, path, partialQuery, apiKey, secretKey, HttpMethod.GET);
    }

    private String signedPost(NetworkType n, String partialQuery, String apiKey, String secretKey) {
        return signedRequest(n, "/api/v3/order", partialQuery, apiKey, secretKey, HttpMethod.POST);
    }

    private void signedDelete(NetworkType n, String partialQuery, String apiKey, String secretKey) {
        signedRequest(n, "/api/v3/order", partialQuery, apiKey, secretKey, HttpMethod.DELETE);
    }

    /* ========== filters cache (exchangeInfo) ========== */

    private record BnFilters(
            BigDecimal tickSize,
            BigDecimal stepSize,
            BigDecimal minQty,
            BigDecimal minNotional,
            String baseAsset,
            String quoteAsset
    ) {}

    private final ConcurrentMap<String, BnFilters> filtersCache = new ConcurrentHashMap<>();

    private BnFilters getFilters(NetworkType n, String symbol) {
        return filtersCache.computeIfAbsent(n.name() + ":" + symbol, k -> fetchFilters(n, symbol));
    }

    private BnFilters fetchFilters(NetworkType network, String symbol) {
        try {
            String url = baseUrl(network) + "/api/v3/exchangeInfo?symbol=" + enc(symbol);
            JsonNode sym = parseJson(rest.getForObject(url, String.class)).path("symbols");
            if (!sym.isArray() || sym.isEmpty()) throw new IllegalStateException("No exchangeInfo for " + symbol);
            JsonNode s = sym.get(0);

            String base = s.path("baseAsset").asText();
            String quote = s.path("quoteAsset").asText();

            BigDecimal tickSize = null, stepSize = null, minQty = null, minNotional = null;
            for (JsonNode f : s.path("filters")) {
                String type = f.path("filterType").asText();
                switch (type) {
                    case "PRICE_FILTER" -> tickSize = new BigDecimal(f.path("tickSize").asText("0"));
                    case "LOT_SIZE" -> {
                        stepSize = new BigDecimal(f.path("stepSize").asText("0"));
                        minQty = new BigDecimal(f.path("minQty").asText("0"));
                    }
                    case "MIN_NOTIONAL", "NOTIONAL" -> {
                        String mn = f.hasNonNull("minNotional")
                                ? f.path("minNotional").asText("0")
                                : f.path("notional").asText("0");
                        minNotional = new BigDecimal(mn);
                    }
                    default -> {}
                }
            }
            return new BnFilters(tickSize, stepSize, minQty, minNotional, base, quote);
        } catch (Exception e) {
            log.warn("Binance exchangeInfo({}) failed: {}", symbol, e.getMessage());
            return new BnFilters(null, null, null, null, null, null);
        }
    }

    /** Квантизация вниз к ближайшему кратному шагу. */
    private static BigDecimal quantizeDown(BigDecimal value, BigDecimal step) {
        if (value == null || step == null || step.signum() == 0) return value;
        int scale = Math.max(0, step.stripTrailingZeros().scale());
        BigDecimal[] div = value.divideAndRemainder(step);
        BigDecimal floored = div[0].multiply(step);
        return floored.setScale(scale, RoundingMode.DOWN).stripTrailingZeros();
    }

    /** Квантизация вверх к ближайшему кратному шагу (важно для minNotional/minQty). */
    private static BigDecimal quantizeUp(BigDecimal value, BigDecimal step) {
        if (value == null || step == null || step.signum() == 0) return value;
        int scale = Math.max(0, step.stripTrailingZeros().scale());
        BigDecimal[] div = value.divideAndRemainder(step);
        BigDecimal base = div[0].multiply(step);
        boolean needsUp = div[1].signum() != 0;
        BigDecimal res = needsUp ? base.add(step) : base;
        return res.setScale(scale, RoundingMode.DOWN).stripTrailingZeros();
    }

    private BigDecimal lastPrice(NetworkType n, String symbol) {
        try {
            String url = baseUrl(n) + "/api/v3/ticker/price?symbol=" + enc(symbol);
            JsonNode j = parseJson(rest.getForObject(url, String.class));
            return new BigDecimal(j.path("price").asText("0"));
        } catch (Exception e) {
            log.warn("Binance lastPrice({}) failed: {}", symbol, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private Map<String, BigDecimal> balances(String apiKey, String secretKey, NetworkType n) {
        try {
            String body = signedGet(n, "/api/v3/account", "", apiKey, secretKey);
            JsonNode acc = parseJson(body);
            Map<String, BigDecimal> map = new HashMap<>();
            for (JsonNode b : acc.path("balances")) {
                map.put(b.path("asset").asText(), new BigDecimal(b.path("free").asText("0")));
            }
            return map;
        } catch (Exception e) {
            log.warn("Binance balances() failed: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /* ========== ExchangeClient ========== */

    @Override
    public boolean testConnection(String apiKey, String secretKey, NetworkType networkType) {
        try {
            signedGet(networkType, "/api/v3/account", "", apiKey, secretKey);
            return true;
        } catch (Exception e) {
            log.warn("Binance testConnection failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public AccountInfo fetchAccountInfo(String apiKey, String secretKey, NetworkType networkType) {
        try {
            String body = signedGet(networkType, "/api/v3/account", "", apiKey, secretKey);
            JsonNode acc = parseJson(body);
            List<BalanceInfo> list = new ArrayList<>();
            for (JsonNode b : acc.path("balances")) {
                list.add(BalanceInfo.builder()
                        .asset(b.path("asset").asText())
                        .free(new BigDecimal(b.path("free").asText("0")))
                        .locked(new BigDecimal(b.path("locked").asText("0")))
                        .build());
            }
            return AccountInfo.builder().balances(list).build();
        } catch (Exception e) {
            log.error("Binance fetchAccountInfo failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch Binance account info", e);
        }
    }

    @Override
    public OrderResponse placeOrder(String apiKey, String secretKey, NetworkType n, OrderRequest req) {
        try {
            String symbol = req.getSymbol();
            BnFilters f = getFilters(n, symbol);
            if (f.tickSize() == null || f.stepSize() == null) {
                throw new RuntimeException("Binance filters not available for " + symbol);
            }

            BigDecimal priceEff = "MARKET".equals(req.getType().name())
                    ? lastPrice(n, symbol)
                    : req.getPrice();

            if (priceEff == null || priceEff.signum() <= 0) {
                throw new IllegalArgumentException("No price available for " + symbol);
            }

            BigDecimal qtyNorm   = quantizeDown(req.getQuantity(), f.stepSize());
            BigDecimal priceNorm = quantizeDown(priceEff, f.tickSize());

            BigDecimal minNotional = Optional.ofNullable(f.minNotional()).orElse(BigDecimal.ZERO);
            BigDecimal minQty      = Optional.ofNullable(f.minQty()).orElse(BigDecimal.ZERO);
            BigDecimal minQtyEff   = quantizeUp(minQty, f.stepSize());

            Map<String, BigDecimal> bals = balances(apiKey, secretKey, n);
            BigDecimal baseFree  = bals.getOrDefault(f.baseAsset(), BigDecimal.ZERO);
            BigDecimal quoteFree = bals.getOrDefault(f.quoteAsset(), BigDecimal.ZERO);

            // собираем часть query без timestamp/recvWindow/signature
            StringBuilder pq = new StringBuilder()
                    .append("symbol=").append(enc(symbol))
                    .append("&side=").append(req.getSide().name())
                    .append("&type=").append(req.getType().name());

            switch (req.getType().name()) {
                case "MARKET" -> {
                    if ("BUY".equals(req.getSide().name())) {
                        BigDecimal desiredSpend = (qtyNorm == null || qtyNorm.signum() == 0)
                                ? quoteFree
                                : priceEff.multiply(qtyNorm);
                        BigDecimal spend = desiredSpend.min(quoteFree);
                        if (spend.compareTo(minNotional) < 0) {
                            throw new RuntimeException("Pre-check: quote balance below minNotional for MARKET BUY");
                        }
                        pq.append("&quoteOrderQty=").append(spend.stripTrailingZeros().toPlainString());
                    } else {
                        BigDecimal needQtyNotional = (minNotional.signum() > 0)
                                ? quantizeUp(minNotional.divide(priceEff, 20, RoundingMode.UP), f.stepSize())
                                : BigDecimal.ZERO;
                        BigDecimal minRequired = needQtyNotional.max(minQtyEff);

                        BigDecimal qty = (qtyNorm == null || qtyNorm.signum() == 0) ? baseFree : qtyNorm;
                        if (qty.compareTo(baseFree) > 0) qty = baseFree;
                        qty = quantizeDown(qty, f.stepSize());
                        if (qty.signum() == 0) {
                            throw new RuntimeException("Pre-check: base balance is zero for MARKET SELL");
                        }
                        if (qty.compareTo(minRequired) < 0) {
                            throw new RuntimeException("Pre-check: quantity below required min for MARKET SELL");
                        }
                        pq.append("&quantity=").append(qty.stripTrailingZeros().toPlainString());
                    }
                }
                case "LIMIT" -> {
                    if (priceNorm == null || priceNorm.signum() == 0) {
                        throw new IllegalArgumentException("Price is zero after quantize");
                    }
                    BigDecimal needQtyNotional = (minNotional.signum() > 0)
                            ? quantizeUp(minNotional.divide(priceNorm, 20, RoundingMode.UP), f.stepSize())
                            : BigDecimal.ZERO;
                    BigDecimal minRequired = needQtyNotional.max(minQtyEff);

                    BigDecimal qty = (qtyNorm == null || qtyNorm.signum() == 0) ? minRequired : qtyNorm;
                    if (qty.compareTo(minRequired) < 0) qty = minRequired;

                    if ("SELL".equals(req.getSide().name())) {
                        if (baseFree.compareTo(qty) < 0) {
                            throw new RuntimeException("Pre-check: insufficient base balance for LIMIT SELL");
                        }
                    } else {
                        BigDecimal needQuote = priceNorm.multiply(qty);
                        if (quoteFree.compareTo(needQuote) < 0) {
                            throw new RuntimeException("Pre-check: insufficient quote balance for LIMIT BUY");
                        }
                    }

                    pq.append("&quantity=").append(qty.stripTrailingZeros().toPlainString())
                            .append("&price=").append(priceNorm.stripTrailingZeros().toPlainString())
                            .append("&timeInForce=GTC");
                }
                default -> {}
            }

            String body = signedPost(n, pq.toString(), apiKey, secretKey);
            JsonNode r = parseJson(body);

            return OrderResponse.builder()
                    .orderId(r.path("orderId").asText(null))
                    .symbol(r.path("symbol").asText(null))
                    .status(r.path("status").asText(null))
                    .executedQty(new BigDecimal(r.path("executedQty").asText("0")))
                    .transactTime(Instant.ofEpochMilli(r.path("transactTime").asLong(System.currentTimeMillis())))
                    .build();

        } catch (RuntimeException ex) {
            log.error("❌ Binance placeOrder pre-check failed: {}", ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            log.error("❌ Binance placeOrder error: {}", ex.getMessage(), ex);
            throw new RuntimeException("Failed to place Binance order", ex);
        }
    }

    @Override
    public List<String> fetchPopularSymbols() {
        try {
            String url = baseUrl(NetworkType.MAINNET) + "/api/v3/ticker/24hr";
            JsonNode arr = parseJson(rest.getForObject(url, String.class));
            List<JsonNode> list = new ArrayList<>();
            arr.forEach(list::add);
            list.sort(Comparator.comparingDouble(n -> -Double.parseDouble(n.path("quoteVolume").asText("0"))));
            return list.stream().limit(100).map(n -> n.path("symbol").asText()).toList();
        } catch (Exception e) {
            log.warn("fetchPopularSymbols failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<String> fetchGainers() {
        try {
            String url = baseUrl(NetworkType.MAINNET) + "/api/v3/ticker/24hr";
            JsonNode arr = parseJson(rest.getForObject(url, String.class));
            List<JsonNode> list = new ArrayList<>();
            arr.forEach(list::add);
            list.sort(Comparator.comparingDouble(n -> -Double.parseDouble(n.path("priceChangePercent").asText("0"))));
            return list.stream().limit(100).map(n -> n.path("symbol").asText()).toList();
        } catch (Exception e) {
            log.warn("fetchGainers failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<String> fetchLosers() {
        try {
            String url = baseUrl(NetworkType.MAINNET) + "/api/v3/ticker/24hr";
            JsonNode arr = parseJson(rest.getForObject(url, String.class));
            List<JsonNode> list = new ArrayList<>();
            arr.forEach(list::add);
            list.sort(Comparator.comparingDouble(n -> Double.parseDouble(n.path("priceChangePercent").asText("0"))));
            return list.stream().limit(100).map(n -> n.path("symbol").asText()).toList();
        } catch (Exception e) {
            log.warn("fetchLosers failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<String> fetchByVolume() {
        return fetchPopularSymbols();
    }


    @Override
    public Optional<TickerInfo> getTicker(String symbol, NetworkType networkType) {
        try {
            String url = baseUrl(networkType) + "/api/v3/ticker/24hr?symbol=" + enc(symbol);
            JsonNode t = parseJson(rest.getForObject(url, String.class));
            BigDecimal last = new BigDecimal(t.path("lastPrice").asText(t.path("weightedAvgPrice").asText("0")));
            BigDecimal pct  = new BigDecimal(t.path("priceChangePercent").asText("0"));
            return Optional.of(TickerInfo.builder().price(last).changePct(pct).build());
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.BAD_REQUEST &&
                e.getResponseBodyAsString().contains("Invalid symbol")) {
                log.warn("❌ Символ {} недоступен на Binance {}", symbol, networkType);
                return Optional.empty();
            }
            throw e;
        } catch (Exception e) {
            log.error("Binance getTicker failed: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }
    @Override
    public List<Candle> fetchCandles(String apiKey, String secretKey, NetworkType n, String symbol, String interval, int limit) {
        try {
            String url = baseUrl(n) + "/api/v3/klines?symbol=" + enc(symbol) + "&interval=" + enc(interval) + "&limit=" + limit;
            JsonNode arr = parseJson(rest.getForObject(url, String.class));
            List<Candle> candles = new ArrayList<>();
            for (JsonNode k : arr) {
                Instant openTime = Instant.ofEpochMilli(k.get(0).asLong());
                BigDecimal open  = new BigDecimal(k.get(1).asText("0"));
                BigDecimal high  = new BigDecimal(k.get(2).asText("0"));
                BigDecimal low   = new BigDecimal(k.get(3).asText("0"));
                BigDecimal close = new BigDecimal(k.get(4).asText("0"));
                BigDecimal vol   = new BigDecimal(k.get(5).asText("0"));
                candles.add(new Candle(symbol, openTime, open, high, low, close, vol));
            }
            return candles;
        } catch (Exception e) {
            log.warn("Binance fetchCandles({} {}) failed: {}", symbol, interval, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<OrderInfo> getOpenOrders(String apiKey, String secretKey, NetworkType n, String symbol) {
        try {
            String body = signedGet(n, "/api/v3/openOrders", "symbol=" + enc(symbol), apiKey, secretKey);
            JsonNode arr = parseJson(body);
            List<OrderInfo> list = new ArrayList<>();
            if (arr.isArray()) {
                for (JsonNode o : arr) {
                    list.add(OrderInfo.builder()
                            .orderId(o.path("orderId").asText(null))
                            .symbol(o.path("symbol").asText(null))
                            .status(o.path("status").asText(null))
                            .executedQty(new BigDecimal(o.path("executedQty").asText("0")))
                            .origQty(new BigDecimal(o.path("origQty").asText("0")))
                            .price(new BigDecimal(o.path("price").asText("0")))
                            .updateTime(Instant.ofEpochMilli(o.path("updateTime").asLong(0)))
                            .build());
                }
            }
            return list;
        } catch (Exception e) {
            log.warn("Binance getOpenOrders({}) failed: {}", symbol, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public OrderInfo getOrder(String apiKey, String secretKey, NetworkType n, String symbol, String orderId) {
        try {
            String pq = "symbol=" + enc(symbol) + "&orderId=" + enc(orderId);
            String body = signedGet(n, "/api/v3/order", pq, apiKey, secretKey);

            JsonNode o = parseJson(body);
            BigDecimal exec = new BigDecimal(o.path("executedQty").asText("0"));
            BigDecimal cummQuote = new BigDecimal(o.path("cummulativeQuoteQty").asText("0"));
            BigDecimal avg = (exec.signum() > 0) ? cummQuote.divide(exec, 20, RoundingMode.HALF_UP) : BigDecimal.ZERO;

            return OrderInfo.builder()
                    .orderId(o.path("orderId").asText(null))
                    .symbol(o.path("symbol").asText(null))
                    .status(o.path("status").asText(null))
                    .executedQty(exec)
                    .origQty(new BigDecimal(o.path("origQty").asText("0")))
                    .price(new BigDecimal(o.path("price").asText("0")))
                    .avgPrice(avg)
                    .updateTime(Instant.ofEpochMilli(o.path("updateTime").asLong(0)))
                    .build();
        } catch (Exception e) {
            log.warn("Binance getOrder({}, {}) failed: {}", symbol, orderId, e.getMessage());
            return null;
        }
    }

    @Override
    public List<OrderInfo> fetchOpenOrders(String apiKey, String secretKey, NetworkType n, String symbol) {
        try {
            String body = signedGet(n, "/api/v3/openOrders", "symbol=" + enc(symbol), apiKey, secretKey);
            JsonNode arr = parseJson(body);
            List<OrderInfo> out = new ArrayList<>();
            for (JsonNode j : arr) {
                out.add(OrderInfo.builder()
                        .orderId(j.path("orderId").asText())
                        .symbol(j.path("symbol").asText())
                        .status(j.path("status").asText())
                        .side("BUY".equalsIgnoreCase(j.path("side").asText()) ? OrderSide.BUY : OrderSide.SELL)
                        .type(com.chicu.aibot.exchange.enums.OrderType.valueOf(j.path("type").asText("LIMIT")))
                        .price(new BigDecimal(j.path("price").asText("0")))
                        .origQty(new BigDecimal(j.path("origQty").asText("0")))
                        .executedQty(new BigDecimal(j.path("executedQty").asText("0")))
                        .updateTime(Instant.ofEpochMilli(j.path("updateTime").asLong(System.currentTimeMillis())))
                        .build());
            }
            return out;
        } catch (Exception e) {
            log.warn("Binance fetchOpenOrders({}) failed: {}", symbol, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public Optional<OrderInfo> fetchOrder(String apiKey, String secretKey, NetworkType n, String symbol, String orderId) {
        try {
            String pq = "symbol=" + enc(symbol) + "&orderId=" + enc(orderId);
            String body = signedGet(n, "/api/v3/order", pq, apiKey, secretKey);
            JsonNode j = parseJson(body);
            OrderInfo info = OrderInfo.builder()
                    .orderId(j.path("orderId").asText())
                    .symbol(j.path("symbol").asText())
                    .status(j.path("status").asText())
                    .side("BUY".equalsIgnoreCase(j.path("side").asText()) ? OrderSide.BUY : OrderSide.SELL)
                    .type(com.chicu.aibot.exchange.enums.OrderType.valueOf(j.path("type").asText("LIMIT")))
                    .price(new BigDecimal(j.path("price").asText("0")))
                    .origQty(new BigDecimal(j.path("origQty").asText("0")))
                    .executedQty(new BigDecimal(j.path("executedQty").asText("0")))
                    .updateTime(Instant.ofEpochMilli(j.path("updateTime").asLong(System.currentTimeMillis())))
                    .build();
            return Optional.of(info);
        } catch (Exception e) {
            log.warn("Binance fetchOrder({}, {}) failed: {}", symbol, orderId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public boolean cancelOrder(String apiKey, String secretKey, NetworkType n, String symbol, String orderId) {
        try {
            String pq = "symbol=" + enc(symbol) + "&orderId=" + enc(orderId);
            signedDelete(n, pq, apiKey, secretKey);
            return true;
        } catch (Exception e) {
            log.warn("Binance cancelOrder({}, {}) failed: {}", symbol, orderId, e.getMessage());
            return false;
        }
    }
}
