package com.chicu.aibot.strategy.service.impl;

import com.chicu.aibot.exchange.client.ExchangeClient;
import com.chicu.aibot.exchange.client.ExchangeClientFactory;
import com.chicu.aibot.exchange.enums.NetworkType;
import com.chicu.aibot.exchange.model.OrderInfo;
import com.chicu.aibot.exchange.model.OrderRequest;
import com.chicu.aibot.exchange.model.OrderResponse;
import com.chicu.aibot.exchange.model.SymbolFilters;
import com.chicu.aibot.exchange.order.model.ExchangeOrderEntity;
import com.chicu.aibot.exchange.order.repository.ExchangeOrderRepository;
import com.chicu.aibot.exchange.service.ExchangeSettingsService;
import com.chicu.aibot.exchange.service.PriceService;
import com.chicu.aibot.exchange.service.SymbolFiltersService;
import com.chicu.aibot.strategy.model.Order;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderExecutionService {

    private final ExchangeClientFactory clientFactory;
    private final ExchangeSettingsService settingsService;
    private final ExchangeOrderRepository orderRepo;

    // пред-проверки цены/фильтров
    private final SymbolFiltersService symbolFiltersService;
    private final PriceService priceService;

    /* ---------- utils ---------- */

    private String normalizeStatus(String raw) {
        if (raw == null) return "NEW";
        String s = raw.trim().toUpperCase(Locale.ROOT).replace(" ", "").replace("_", "");
        return switch (s) {
            case "FILLED" -> "FILLED";
            case "PARTIALLYFILLED" -> "PARTIALLY_FILLED";
            case "NEW" -> "NEW";
            case "CANCELLED", "CANCELED" -> "CANCELED";
            case "REJECTED" -> "REJECTED";
            case "EXPIRED" -> "EXPIRED";
            default -> s;
        };
    }

    private com.chicu.aibot.exchange.enums.OrderSide mapSide(Order.Side side) {
        return (side == Order.Side.BUY)
                ? com.chicu.aibot.exchange.enums.OrderSide.BUY
                : com.chicu.aibot.exchange.enums.OrderSide.SELL;
    }

    private static BigDecimal roundToStep(BigDecimal qty, BigDecimal step) {
        if (qty == null || qty.signum() <= 0) return BigDecimal.ZERO;
        if (step == null || step.signum() <= 0) return qty;
        return qty.divide(step, 0, RoundingMode.DOWN).multiply(step);
    }

    private static double bdToDouble(BigDecimal v) {
        return v == null ? 0.0 : v.doubleValue();
    }

    /* ---------- LIMIT ---------- */

    @Transactional
    public Order placeLimit(Long chatId, String symbol, Order.Side side, double price, double quantity) {
        var settings = settingsService.getOrCreate(chatId);
        var keys     = settingsService.getApiKey(chatId);
        ExchangeClient client = clientFactory.getClient(settings.getExchange());

        SymbolFilters filters = symbolFiltersService.getFilters(settings.getExchange(), symbol, settings.getNetwork());
        BigDecimal stepSize   = filters.getStepSize();
        BigDecimal minQty     = filters.getMinQty();
        BigDecimal minNotional= filters.getMinNotional();

        BigDecimal p = BigDecimal.valueOf(price);
        BigDecimal q = roundToStep(BigDecimal.valueOf(quantity), stepSize);

        if (q.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("⛔️ Пропускаем LIMIT {} {}: qty <= 0 (после stepSize={})", side, symbol, stepSize);
            return saveRejected(chatId, settings.getExchange().name(), settings.getNetwork(),
                    symbol, side, "LIMIT", price, 0.0, "Qty <= 0");
        }
        if (minQty != null && q.compareTo(minQty) < 0) {
            log.warn("⛔️ Пропускаем LIMIT {} {}: qty < minQty ({} < {})", side, symbol, q, minQty);
            return saveRejected(chatId, settings.getExchange().name(), settings.getNetwork(),
                    symbol, side, "LIMIT", price, q.doubleValue(), "Qty < minQty");
        }
        if (minNotional != null && q.multiply(p).compareTo(minNotional) < 0) {
            log.warn("⛔️ Пропускаем LIMIT {} {}: notional < minNotional ({} < {})",
                    side, symbol, q.multiply(p), minNotional);
            return saveRejected(chatId, settings.getExchange().name(), settings.getNetwork(),
                    symbol, side, "LIMIT", price, q.doubleValue(), "Notional < minNotional");
        }

        var req = OrderRequest.builder()
                .symbol(symbol)
                .side(mapSide(side))
                .type(com.chicu.aibot.exchange.enums.OrderType.LIMIT)
                .price(p)
                .quantity(q)
                .build();

        OrderResponse resp;
        try {
            resp = client.placeOrder(keys.getPublicKey(), keys.getSecretKey(), settings.getNetwork(), req);
        } catch (Exception e) {
            log.warn("❌ LIMIT {} {} qty={} price={} ошибка={}", side, symbol, q, p, e.getMessage());
            return saveRejected(chatId, settings.getExchange().name(), settings.getNetwork(),
                    symbol, side, "LIMIT", price, q.doubleValue(), e.getMessage());
        }

        // Попробуем запросить детали ордера (avg price, executed, финальный статус)
        OrderInfo fetched = fetchOrderSafe(client, keys.getPublicKey(), keys.getSecretKey(),
                settings.getNetwork(), symbol, resp.getOrderId());

        BigDecimal usedPrice = (fetched != null && fetched.getAvgPrice() != null && fetched.getAvgPrice().signum() > 0)
                ? fetched.getAvgPrice()
                : p;

        BigDecimal executedQty = (fetched != null && fetched.getExecutedQty() != null)
                ? fetched.getExecutedQty()
                : (resp.getExecutedQty() != null ? resp.getExecutedQty() : BigDecimal.ZERO);

        String status = fetched != null ? fetched.getStatus() : resp.getStatus();

        return saveExecuted(chatId, settings.getExchange().name(), settings.getNetwork(),
                side, "LIMIT", bdToDouble(usedPrice), q.doubleValue(), resp.getOrderId(),
                resp.getSymbol(), executedQty, status);
    }

    /* ---------- MARKET ---------- */

    @Transactional
    public Order placeMarket(Long chatId, String symbol, Order.Side side, double quantity) {
        var settings = settingsService.getOrCreate(chatId);
        var keys     = settingsService.getApiKey(chatId);
        ExchangeClient client = clientFactory.getClient(settings.getExchange());

        // фильтры + текущая цена для notional
        SymbolFilters filters = symbolFiltersService.getFilters(settings.getExchange(), symbol, settings.getNetwork());
        BigDecimal lastPrice  = priceService.getLastPrice(settings.getExchange(), symbol, settings.getNetwork());
        BigDecimal stepSize   = filters.getStepSize();
        BigDecimal minQty     = filters.getMinQty();
        BigDecimal minNotional= filters.getMinNotional();

        BigDecimal q = roundToStep(BigDecimal.valueOf(quantity), stepSize);

        if (q.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("⛔️ Пропускаем MARKET {} {}: qty <= 0 (после stepSize={})", side, symbol, stepSize);
            return saveRejected(chatId, settings.getExchange().name(), settings.getNetwork(),
                    symbol, side, "MARKET", bdToDouble(lastPrice), 0.0, "Qty <= 0");
        }
        if (minQty != null && q.compareTo(minQty) < 0) {
            log.warn("⛔️ Пропускаем MARKET {} {}: qty < minQty ({} < {})", side, symbol, q, minQty);
            return saveRejected(chatId, settings.getExchange().name(), settings.getNetwork(),
                    symbol, side, "MARKET", bdToDouble(lastPrice), q.doubleValue(), "Qty < minQty");
        }
        if (lastPrice != null && minNotional != null && q.multiply(lastPrice).compareTo(minNotional) < 0) {
            log.warn("⛔️ Пропускаем MARKET {} {}: notional < minNotional ({} < {})",
                    side, symbol, q.multiply(lastPrice), minNotional);
            return saveRejected(chatId, settings.getExchange().name(), settings.getNetwork(),
                    symbol, side, "MARKET", bdToDouble(lastPrice), q.doubleValue(), "Notional < minNotional");
        }

        var req = OrderRequest.builder()
                .symbol(symbol)
                .side(mapSide(side))
                .type(com.chicu.aibot.exchange.enums.OrderType.MARKET)
                .quantity(q)
                .build();

        OrderResponse resp;
        try {
            resp = client.placeOrder(keys.getPublicKey(), keys.getSecretKey(), settings.getNetwork(), req);
        } catch (Exception e) {
            log.warn("❌ MARKET {} {} qty={} ошибка={}", side, symbol, q, e.getMessage());
            return saveRejected(chatId, settings.getExchange().name(), settings.getNetwork(),
                    symbol, side, "MARKET", bdToDouble(lastPrice), q.doubleValue(), e.getMessage());
        }

        // Дотягиваем фактические данные (avg price, executed, статус)
        OrderInfo fetched = fetchOrderSafe(client, keys.getPublicKey(), keys.getSecretKey(),
                settings.getNetwork(), symbol, resp.getOrderId());

        BigDecimal usedPrice = (fetched != null && fetched.getAvgPrice() != null && fetched.getAvgPrice().signum() > 0)
                ? fetched.getAvgPrice()
                : (lastPrice != null ? lastPrice : BigDecimal.ZERO);

        BigDecimal executedQty = (fetched != null && fetched.getExecutedQty() != null)
                ? fetched.getExecutedQty()
                : (resp.getExecutedQty() != null ? resp.getExecutedQty() : q); // fallback — заданный объём

        String status = fetched != null ? fetched.getStatus() : resp.getStatus();

        return saveExecuted(chatId, settings.getExchange().name(), settings.getNetwork(),
                side, "MARKET", bdToDouble(usedPrice), q.doubleValue(), resp.getOrderId(),
                resp.getSymbol(), executedQty, status);
    }

    private OrderInfo fetchOrderSafe(ExchangeClient client,
                                     String apiKey, String secretKey, NetworkType net,
                                     String symbol, String orderId) {
        if (orderId == null || symbol == null) return null;
        try {
            Optional<OrderInfo> opt = client.fetchOrder(apiKey, secretKey, net, symbol, orderId);
            return opt.orElse(null);
        } catch (Exception e) {
            log.debug("fetchOrderSafe({}, {}) error: {}", symbol, orderId, e.getMessage());
            return null;
        }
    }

    /* ---------- persistence ---------- */

    private Order saveRejected(Long chatId, String exchange, NetworkType network,
                               String symbol, Order.Side side, String type,
                               double price, double quantity, String reason) {
        Instant now = Instant.now();
        ExchangeOrderEntity entity = ExchangeOrderEntity.builder()
                .chatId(chatId)
                .exchange(exchange)
                .network(network)
                .orderId("REJECTED-" + UUID.randomUUID())
                .symbol(symbol)
                .side(side.name())
                .type(type)
                .price(BigDecimal.valueOf(price))
                .quantity(BigDecimal.valueOf(quantity))
                .executedQty(BigDecimal.ZERO)
                .quoteQty(BigDecimal.ZERO)
                .commission(BigDecimal.ZERO)
                .commissionAsset("NONE")
                .status("REJECTED")
                .createdAt(now)
                .updatedAt(now)
                .build();

        orderRepo.save(entity);
        log.info("💾 REJECTED ордер {} {} qty={} причина={}", side, symbol, quantity, reason);

        return new Order(
                entity.getOrderId(),
                symbol,
                side,
                price,
                0.0,
                false,   // filled
                true,    // cancelled
                false,   // closed
                true     // rejected
        );
    }

    private Order saveExecuted(Long chatId, String exchange, NetworkType network,
                               Order.Side side, String type,
                               double priceUsed, double quantityRequested,
                               String orderId, String respSymbol,
                               BigDecimal executedQty, String rawStatus) {
        Instant now = Instant.now();

        String status = normalizeStatus(rawStatus);
        BigDecimal usedPrice = BigDecimal.valueOf(priceUsed);
        BigDecimal qtyReq    = BigDecimal.valueOf(quantityRequested);
        BigDecimal execQty   = executedQty != null ? executedQty : BigDecimal.ZERO;

        ExchangeOrderEntity entity = ExchangeOrderEntity.builder()
                .chatId(chatId)
                .exchange(exchange)
                .network(network)
                .orderId(orderId != null ? orderId : ("LOCAL-" + UUID.randomUUID()))
                .symbol(respSymbol != null ? respSymbol : "UNKNOWN")
                .side(side.name())
                .type(type)
                .price(usedPrice)
                .quantity(qtyReq)
                .executedQty(execQty)
                .quoteQty(usedPrice.multiply(execQty))
                .commission(BigDecimal.ZERO)           // нет из ответа — оставляем 0
                .commissionAsset("UNKNOWN")
                .status(status != null ? status : "NEW")
                .createdAt(now)
                .updatedAt(now)
                .build();

        orderRepo.save(entity);

        log.info("💾 Ордер сохранён: {} {} qty={} @{} статус={} комиссия={} {}",
                side, entity.getSymbol(), entity.getExecutedQty(), entity.getPrice(),
                entity.getStatus(), entity.getCommission(), entity.getCommissionAsset());

        return new Order(
                entity.getOrderId(),
                entity.getSymbol(),
                side,
                bdToDouble(entity.getPrice()),
                bdToDouble(entity.getExecutedQty()),
                "FILLED".equalsIgnoreCase(entity.getStatus()),
                "CANCELED".equalsIgnoreCase(entity.getStatus()) || "EXPIRED".equalsIgnoreCase(entity.getStatus()),
                false,
                "REJECTED".equalsIgnoreCase(entity.getStatus())
        );
    }
}
