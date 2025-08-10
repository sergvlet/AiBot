package com.chicu.aibot.exchange.order.service;

import com.chicu.aibot.exchange.enums.NetworkType;
import com.chicu.aibot.exchange.model.OrderRequest;
import com.chicu.aibot.exchange.model.OrderResponse;
import com.chicu.aibot.exchange.order.model.ExchangeOrderEntity;
import com.chicu.aibot.exchange.order.repository.ExchangeOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ExchangeOrderDbService {

    private final ExchangeOrderRepository repo;

    private static final Set<String> OPEN = Set.of("NEW","PARTIALLY_FILLED");
    private static final Set<String> CLOSED = Set.of("FILLED","CANCELED","REJECTED","EXPIRED");

    @Transactional
    public ExchangeOrderEntity savePlaced(Long chatId, String exchange, NetworkType network,
                                          OrderRequest req, OrderResponse resp) {
        Instant now = Instant.now();
        ExchangeOrderEntity e = repo.findByChatIdAndExchangeAndNetworkAndOrderId(
                chatId, exchange, network, resp.getOrderId()).orElseGet(ExchangeOrderEntity::new);

        e.setChatId(chatId);
        e.setExchange(exchange);
        e.setNetwork(network);
        e.setOrderId(resp.getOrderId());
        e.setSymbol(resp.getSymbol());
        e.setSide(req.getSide().name());
        e.setType(req.getType().name());
        e.setPrice(req.getPrice());
        e.setQuantity(req.getQuantity());
        e.setExecutedQty(resp.getExecutedQty());
        e.setStatus(resp.getStatus() == null ? "NEW" : resp.getStatus());
        if (e.getCreatedAt() == null) e.setCreatedAt(now);
        e.setUpdatedAt(now);
        e.setLastCheckedAt(now);
        return repo.save(e);
    }

    public boolean hasOpenOrderAtPrice(Long chatId, String symbol, String side, BigDecimal price) {
        return !repo.findOpenByPrice(chatId, symbol, side, price, OPEN).isEmpty();
    }

    public List<ExchangeOrderEntity> findOpenByChatAndSymbol(Long chatId, String symbol) {
        return repo.findByChatIdAndSymbolAndStatusIn(chatId, symbol, OPEN);
    }

    public List<ExchangeOrderEntity> findFilledBuys(Long chatId, String symbol) {
        return repo.findByChatIdAndSymbolAndSideAndStatusIn(chatId, symbol, "BUY", Set.of("FILLED"));
    }

    @Transactional
    public void updateFromExchange(Long chatId, String exchange, NetworkType network,
                                   String orderId, String status, BigDecimal executedQty) {
        repo.findByChatIdAndExchangeAndNetworkAndOrderId(chatId, exchange, network, orderId)
            .ifPresent(e -> {
                e.setStatus(status);
                e.setExecutedQty(executedQty);
                e.setUpdatedAt(Instant.now());
                e.setLastCheckedAt(Instant.now());
                repo.save(e);
            });
    }

    public List<ExchangeOrderEntity> findAllOpen() {
        return repo.findByStatusIn(OPEN);
    }
}
