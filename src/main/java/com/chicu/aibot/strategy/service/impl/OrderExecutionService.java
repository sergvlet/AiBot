package com.chicu.aibot.strategy.service.impl;

import com.chicu.aibot.exchange.client.ExchangeClient;
import com.chicu.aibot.exchange.client.ExchangeClientFactory;
import com.chicu.aibot.exchange.enums.NetworkType;
import com.chicu.aibot.exchange.model.*;
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
import java.util.List;
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

    /** Определяет базовый актив по известным суффиксам (USDT, USDC, BUSD, BTC, ETH и т.д.) */
    private static String extractBaseAsset(String symbol) {
        if (symbol == null) return "UNKNOWN";
        String[] knownQuotes = {"USDT","USDC","BUSD","FDUSD","TUSD","DAI","BTC","ETH","BNB","TRY","EUR","JPY","BIDR","AUD","BRL","GBP","RUB","UAH"};
        for (String q : knownQuotes) {
            if (symbol.endsWith(q)) {
                return symbol.substring(0, symbol.length() - q.length());
            }
        }
        return symbol; // fallback
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

        // цену НЕ округляем по тик-сайзу (его нет в SymbolFilters)
        BigDecimal p = BigDecimal.valueOf(price);
        BigDecimal q = roundToStep(BigDecimal.valueOf(quantity), stepSize);

        // анти-дубль: уже есть открытый такой же LIMIT (symbol/side/price/qty)?
        var openStatuses = List.of("NEW", "PARTIALLY_FILLED");
        Optional<ExchangeOrderEntity> dup = orderRepo.findTopByChatIdAndExchangeAndNetworkAndSymbolAndSideAndTypeAndPriceAndQuantityAndStatusInOrderByCreatedAtDesc(
                chatId,
                settings.getExchange().name(),
                settings.getNetwork(),
                symbol,
                side.name(),
                "LIMIT",
                p,
                q,
                openStatuses
        );
        if (dup.isPresent()) {
            log.info("⛔️ Пропускаем дубль LIMIT {} {}: уже есть открытый ордер @{} qty={}", side, symbol, p, q);
            return toDomain(dup.get());
        }

        if (q.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("⛔️ LIMIT {} {}: qty <= 0 (после stepSize={})", side, symbol, stepSize);
            return saveRejected(chatId, settings.getExchange().name(), settings.getNetwork(),
                    symbol, side, "LIMIT", bdToDouble(p), 0.0, "Qty <= 0");
        }
        if (minQty != null && q.compareTo(minQty) < 0) {
            log.warn("⛔️ LIMIT {} {}: qty < minQty ({} < {})", side, symbol, q, minQty);
            return saveRejected(chatId, settings.getExchange().name(), settings.getNetwork(),
                    symbol, side, "LIMIT", bdToDouble(p), q.doubleValue(), "Qty < minQty");
        }
        if (minNotional != null && q.multiply(p).compareTo(minNotional) < 0) {
            log.warn("⛔️ LIMIT {} {}: notional < minNotional ({} < {})",
                    side, symbol, q.multiply(p), minNotional);
            return saveRejected(chatId, settings.getExchange().name(), settings.getNetwork(),
                    symbol, side, "LIMIT", bdToDouble(p), q.doubleValue(), "Notional < minNotional");
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
                    symbol, side, "LIMIT", bdToDouble(p), q.doubleValue(), e.getMessage());
        }

        // Подтягиваем финальные данные (avg price, executed, статус)
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

        // Для MARKET SELL с qty=0 продаём весь доступный базовый актив (определяем базу из символа)
        if (side == Order.Side.SELL && q.compareTo(BigDecimal.ZERO) <= 0) {
            try {
                AccountInfo acc = client.fetchAccountInfo(keys.getPublicKey(), keys.getSecretKey(), settings.getNetwork());
                String baseAsset = extractBaseAsset(symbol);
                BigDecimal baseFree = acc.getBalances().stream()
                        .filter(b -> baseAsset.equalsIgnoreCase(b.getAsset()))
                        .map(BalanceInfo::getFree)
                        .findFirst()
                        .orElse(BigDecimal.ZERO);

                q = roundToStep(baseFree, stepSize);
                log.info("MARKET SELL {} без qty -> используем весь свободный баланс {} = {}", symbol, baseAsset, q);

                if (q.compareTo(BigDecimal.ZERO) <= 0) {
                    return saveRejected(chatId, settings.getExchange().name(), settings.getNetwork(),
                            symbol, side, "MARKET", bdToDouble(lastPrice), 0.0, "Base balance is zero");
                }
            } catch (Exception e) {
                log.warn("Не удалось получить баланс для MARKET SELL {}: {}", symbol, e.getMessage());
                return saveRejected(chatId, settings.getExchange().name(), settings.getNetwork(),
                        symbol, side, "MARKET", bdToDouble(lastPrice), 0.0, "Failed to fetch base balance");
            }
        }

        // анти-дубль для MARKET: по qty + side (цену не учитываем, у MARKET её может не быть)
        var openStatuses = List.of("NEW", "PARTIALLY_FILLED");
        Optional<ExchangeOrderEntity> dup = orderRepo.findTopByChatIdAndExchangeAndNetworkAndSymbolAndSideAndTypeAndPriceAndQuantityAndStatusInOrderByCreatedAtDesc(
                chatId,
                settings.getExchange().name(),
                settings.getNetwork(),
                symbol,
                side.name(),
                "MARKET",
                lastPrice == null ? BigDecimal.ZERO : lastPrice, // храним lastPrice либо 0
                q,
                openStatuses
        );
        if (dup.isPresent()) {
            log.info("⛔️ Пропускаем дубль MARKET {} {} qty={} (уже есть открытый)", side, symbol, q);
            return toDomain(dup.get());
        }

        if (q.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("⛔️ MARKET {} {}: qty <= 0 (после stepSize={})", side, symbol, stepSize);
            return saveRejected(chatId, settings.getExchange().name(), settings.getNetwork(),
                    symbol, side, "MARKET", bdToDouble(lastPrice), 0.0, "Qty <= 0");
        }
        if (minQty != null && q.compareTo(minQty) < 0) {
            log.warn("⛔️ MARKET {} {}: qty < minQty ({} < {})", side, symbol, q, minQty);
            return saveRejected(chatId, settings.getExchange().name(), settings.getNetwork(),
                    symbol, side, "MARKET", bdToDouble(lastPrice), q.doubleValue(), "Qty < minQty");
        }
        if (lastPrice != null && minNotional != null && q.multiply(lastPrice).compareTo(minNotional) < 0) {
            log.warn("⛔️ MARKET {} {}: notional < minNotional ({} < {})",
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
                : (resp.getExecutedQty() != null ? resp.getExecutedQty() : q); // fallback — заданный/подставленный объём

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

        return toDomain(entity);
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
                .commission(BigDecimal.ZERO)
                .commissionAsset("UNKNOWN")
                .status(status != null ? status : "NEW")
                .createdAt(now)
                .updatedAt(now)
                .build();

        orderRepo.save(entity);

        log.info("💾 Ордер сохранён: {} {} reqQty={} execQty={} @{} статус={} комиссия={} {}",
                side, entity.getSymbol(), entity.getQuantity(), entity.getExecutedQty(), entity.getPrice(),
                entity.getStatus(), entity.getCommission(), entity.getCommissionAsset());

        return toDomain(entity);
    }

    private Order toDomain(ExchangeOrderEntity e) {
        return new Order(
                e.getOrderId(),
                e.getSymbol(),
                Order.Side.valueOf(e.getSide()),
                bdToDouble(e.getPrice()),
                bdToDouble(e.getExecutedQty()),
                "FILLED".equalsIgnoreCase(e.getStatus()),
                "CANCELED".equalsIgnoreCase(e.getStatus()) || "EXPIRED".equalsIgnoreCase(e.getStatus()),
                false,
                "REJECTED".equalsIgnoreCase(e.getStatus())
        );
    }
}
