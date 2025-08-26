package com.chicu.aibot.strategy.service.impl;

import com.chicu.aibot.exchange.client.ExchangeClientFactory;
import com.chicu.aibot.exchange.enums.NetworkType;
import com.chicu.aibot.exchange.mapper.OrderResponseMapperFactory;
import com.chicu.aibot.exchange.model.OrderRequest;
import com.chicu.aibot.exchange.model.OrderResponse;
import com.chicu.aibot.exchange.order.model.ExchangeOrderEntity;
import com.chicu.aibot.exchange.order.repository.ExchangeOrderRepository;
import com.chicu.aibot.exchange.service.ExchangeSettingsService;
import com.chicu.aibot.strategy.model.Order;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
    private final OrderResponseMapperFactory mapperFactory;

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

    @Transactional
    public Order placeLimit(Long chatId, String symbol, Order.Side side, double price, double quantity) {
        var settings = settingsService.getOrCreate(chatId);
        var keys     = settingsService.getApiKey(chatId);
        var client   = clientFactory.getClient(settings.getExchange());

        var req = OrderRequest.builder()
                .symbol(symbol)
                .side(mapSide(side))
                .type(com.chicu.aibot.exchange.enums.OrderType.LIMIT)
                .price(BigDecimal.valueOf(price))
                .quantity(BigDecimal.valueOf(quantity))
                .build();

        OrderResponse resp;
        try {
            var rawResp = client.placeOrder(keys.getPublicKey(), keys.getSecretKey(), settings.getNetwork(), req);
            var mapper  = mapperFactory.getMapper(settings.getExchange());
            resp = mapper.map(rawResp);
        } catch (Exception e) {
            log.warn("❌ placeLimit отклонён биржей: {} {} qty={} price={} причина={}",
                    side, symbol, quantity, price, e.getMessage());

            return saveRejected(
                    chatId,
                    settings.getExchange().name(),
                    settings.getNetwork(),
                    symbol,
                    side,
                    "LIMIT",
                    price,
                    quantity,
                    e.getMessage()
            );
        }

        return saveExecuted(chatId, settings.getExchange().name(), settings.getNetwork(),
                side, "LIMIT", price, quantity, resp);
    }

    @Transactional
    public Order placeMarket(Long chatId, String symbol, Order.Side side, double quantity) {
        var settings = settingsService.getOrCreate(chatId);
        var keys     = settingsService.getApiKey(chatId);
        var client   = clientFactory.getClient(settings.getExchange());

        var req = OrderRequest.builder()
                .symbol(symbol)
                .side(mapSide(side))
                .type(com.chicu.aibot.exchange.enums.OrderType.MARKET)
                .quantity(BigDecimal.valueOf(quantity))
                .build();

        OrderResponse resp;
        try {
            var rawResp = client.placeOrder(keys.getPublicKey(), keys.getSecretKey(), settings.getNetwork(), req);
            var mapper  = mapperFactory.getMapper(settings.getExchange());
            resp = mapper.map(rawResp);
        } catch (Exception e) {
            log.warn("❌ placeMarket отклонён биржей: {} {} qty={} причина={}",
                    side, symbol, quantity, e.getMessage());

            return saveRejected(
                    chatId,
                    settings.getExchange().name(),
                    settings.getNetwork(),
                    symbol,
                    side,
                    "MARKET",
                    0.0,
                    quantity,
                    e.getMessage()
            );
        }

        double usedPrice = (resp.getPrice() != null) ? resp.getPrice().doubleValue() : 0.0;
        return saveExecuted(chatId, settings.getExchange().name(), settings.getNetwork(),
                side, "MARKET", usedPrice, quantity, resp);
    }

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
                .commissionAsset("USDT")
                .status("REJECTED")
                .createdAt(now)
                .updatedAt(now)
                .build();

        orderRepo.save(entity);

        log.info("💾 Сохранён REJECTED ордер: {} {} qty={} причина={}", side, symbol, quantity, reason);
        return new Order(entity.getOrderId(), symbol, side, price, 0.0, false, false, false);
    }

    private Order saveExecuted(Long chatId, String exchange, NetworkType network,
                               Order.Side side, String type,
                               double price, double quantity, OrderResponse resp) {
        Instant now = Instant.now();
        ExchangeOrderEntity entity = ExchangeOrderEntity.builder()
                .chatId(chatId)
                .exchange(exchange)
                .network(network)
                .orderId(resp.getOrderId())
                .symbol(resp.getSymbol() != null ? resp.getSymbol() : "UNKNOWN")
                .side(side.name())
                .type(type)
                .price(resp.getPrice() != null ? resp.getPrice() : BigDecimal.valueOf(price))
                .quantity(BigDecimal.valueOf(quantity))
                .executedQty(resp.getExecutedQty() != null ? resp.getExecutedQty() : BigDecimal.ZERO)
                .quoteQty(resp.getPrice() != null && resp.getExecutedQty() != null
                        ? resp.getPrice().multiply(resp.getExecutedQty()) : BigDecimal.ZERO)
                .commission(resp.getCommission() != null ? resp.getCommission() : BigDecimal.ZERO)
                .commissionAsset(resp.getCommissionAsset() != null ? resp.getCommissionAsset() : "UNKNOWN")
                .status(normalizeStatus(resp.getStatus()))
                .createdAt(now)
                .updatedAt(now)
                .build();

        orderRepo.save(entity);

        log.info("💾 Сохранён ордер {} {} qty={} @{} статус={}",
                side, entity.getSymbol(), quantity, entity.getPrice(), entity.getStatus());

        return new Order(
                entity.getOrderId(),
                entity.getSymbol(),
                side,
                entity.getPrice() != null ? entity.getPrice().doubleValue() : 0.0,
                entity.getExecutedQty() != null ? entity.getExecutedQty().doubleValue() : 0.0,
                "FILLED".equalsIgnoreCase(entity.getStatus()),
                false,
                false
        );
    }
}
