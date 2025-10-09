package com.chicu.aibot.strategy.service.impl;

import com.chicu.aibot.exchange.client.ExchangeClientFactory;
import com.chicu.aibot.exchange.enums.NetworkType;
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

    /* ---------- LIMIT ---------- */

    @Transactional
    public Order placeLimit(Long chatId, String symbol, Order.Side side, double price, double quantity) {
        var settings = settingsService.getOrCreate(chatId);
        var keys     = settingsService.getApiKey(chatId);
        var client   = clientFactory.getClient(settings.getExchange());

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
            // ВАЖНО: клиент уже возвращает OrderResponse — ничего маппить не нужно
            resp = client.placeOrder(keys.getPublicKey(), keys.getSecretKey(), settings.getNetwork(), req);
        } catch (Exception e) {
            log.warn("❌ LIMIT {} {} qty={} price={} ошибка={}", side, symbol, q, p, e.getMessage());
            return saveRejected(chatId, settings.getExchange().name(), settings.getNetwork(),
                    symbol, side, "LIMIT", price, q.doubleValue(), e.getMessage());
        }

        return saveExecuted(chatId, settings.getExchange().name(), settings.getNetwork(),
                side, "LIMIT", p.doubleValue(), q.doubleValue(), resp);
    }

    /* ---------- MARKET ---------- */

    @Transactional
    public Order placeMarket(Long chatId, String symbol, Order.Side side, double quantity) {
        var settings = settingsService.getOrCreate(chatId);
        var keys     = settingsService.getApiKey(chatId);
        var client   = clientFactory.getClient(settings.getExchange());

        // фильтры + текущая цена для notional
        SymbolFilters filters = symbolFiltersService.getFilters(settings.getExchange(), symbol, settings.getNetwork());
        BigDecimal price      = priceService.getLastPrice(settings.getExchange(), symbol, settings.getNetwork());
        BigDecimal stepSize   = filters.getStepSize();
        BigDecimal minQty     = filters.getMinQty();
        BigDecimal minNotional= filters.getMinNotional();

        BigDecimal q = roundToStep(BigDecimal.valueOf(quantity), stepSize);

        if (q.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("⛔️ Пропускаем MARKET {} {}: qty <= 0 (после stepSize={})", side, symbol, stepSize);
            return saveRejected(chatId, settings.getExchange().name(), settings.getNetwork(),
                    symbol, side, "MARKET", price != null ? price.doubleValue() : 0.0,
                    0.0, "Qty <= 0");
        }
        if (minQty != null && q.compareTo(minQty) < 0) {
            log.warn("⛔️ Пропускаем MARKET {} {}: qty < minQty ({} < {})", side, symbol, q, minQty);
            return saveRejected(chatId, settings.getExchange().name(), settings.getNetwork(),
                    symbol, side, "MARKET", price != null ? price.doubleValue() : 0.0,
                    q.doubleValue(), "Qty < minQty");
        }
        if (price != null && minNotional != null && q.multiply(price).compareTo(minNotional) < 0) {
            log.warn("⛔️ Пропускаем MARKET {} {}: notional < minNotional ({} < {})",
                    side, symbol, q.multiply(price), minNotional);
            return saveRejected(chatId, settings.getExchange().name(), settings.getNetwork(),
                    symbol, side, "MARKET", price.doubleValue(), q.doubleValue(),
                    "Notional < minNotional");
        }

        var req = OrderRequest.builder()
                .symbol(symbol)
                .side(mapSide(side))
                .type(com.chicu.aibot.exchange.enums.OrderType.MARKET)
                .quantity(q)
                .build();

        OrderResponse resp;
        try {
            // ВАЖНО: клиент уже возвращает OrderResponse — ничего маппить не нужно
            resp = client.placeOrder(keys.getPublicKey(), keys.getSecretKey(), settings.getNetwork(), req);
        } catch (Exception e) {
            log.warn("❌ MARKET {} {} qty={} ошибка={}", side, symbol, q, e.getMessage());
            return saveRejected(chatId, settings.getExchange().name(), settings.getNetwork(),
                    symbol, side, "MARKET",
                    price != null ? price.doubleValue() : 0.0,
                    q.doubleValue(), e.getMessage());
        }

        // фактическая цена (если биржа её не дала — оцениваем по тикеру)
        double usedPrice = (resp.getPrice() != null)
                ? resp.getPrice().doubleValue()
                : (resp.getExecutedQty() != null && resp.getQuoteQty() != null
                ? resp.getQuoteQty().divide(resp.getExecutedQty(), 8, RoundingMode.HALF_UP).doubleValue()
                : (price != null ? price.doubleValue() : 0.0));

        return saveExecuted(chatId, settings.getExchange().name(), settings.getNetwork(),
                side, "MARKET", usedPrice, q.doubleValue(), resp);
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
                               double price, double quantity, OrderResponse resp) {
        Instant now = Instant.now();

        BigDecimal usedPrice = (resp.getPrice() != null) ? resp.getPrice() : BigDecimal.valueOf(price);
        BigDecimal usedQty   = (resp.getExecutedQty() != null) ? resp.getExecutedQty() : BigDecimal.valueOf(quantity);
        BigDecimal quoteQty  = (resp.getQuoteQty() != null)
                ? resp.getQuoteQty()
                : usedPrice.multiply(usedQty);

        ExchangeOrderEntity entity = ExchangeOrderEntity.builder()
                .chatId(chatId)
                .exchange(exchange)
                .network(network)
                .orderId(resp.getOrderId() != null ? resp.getOrderId() : ("LOCAL-" + UUID.randomUUID()))
                .symbol(resp.getSymbol() != null ? resp.getSymbol() : "UNKNOWN")
                .side(side.name())
                .type(type)
                .price(usedPrice)
                .quantity(BigDecimal.valueOf(quantity))
                .executedQty(usedQty)
                .quoteQty(quoteQty)
                .commission(resp.getCommission() != null ? resp.getCommission() : BigDecimal.ZERO)
                .commissionAsset(resp.getCommissionAsset() != null ? resp.getCommissionAsset() : "UNKNOWN")
                .status(normalizeStatus(resp.getStatus()))
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
                entity.getPrice() != null ? entity.getPrice().doubleValue() : 0.0,
                entity.getExecutedQty() != null ? entity.getExecutedQty().doubleValue() : 0.0,
                "FILLED".equalsIgnoreCase(entity.getStatus()),
                false,
                false,
                false
        );
    }
}
