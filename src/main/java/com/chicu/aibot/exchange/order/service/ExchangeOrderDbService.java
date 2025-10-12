package com.chicu.aibot.exchange.order.service;

import com.chicu.aibot.exchange.enums.NetworkType;
import com.chicu.aibot.exchange.model.OrderRequest;
import com.chicu.aibot.exchange.model.OrderResponse;
import com.chicu.aibot.exchange.order.model.ExchangeOrderEntity;
import com.chicu.aibot.exchange.order.repository.ExchangeOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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

    private static final Set<String> OPEN   = Set.of("NEW", "PARTIALLY_FILLED");
    private static final Set<String> CLOSED = Set.of("FILLED", "CANCELED", "REJECTED", "EXPIRED");

    /**
     * Создаёт/обновляет запись ордера после размещения на бирже.
     */
    @Transactional
    public ExchangeOrderEntity savePlaced(Long chatId,
                                          String exchange,
                                          NetworkType network,
                                          OrderRequest req,
                                          OrderResponse resp,
                                          BigDecimal pnl,
                                          BigDecimal pnlPct) {
        Instant now = Instant.now();

        ExchangeOrderEntity e = repo.findByChatIdAndExchangeAndNetworkAndOrderId(
                chatId, exchange, network, resp.getOrderId()
        ).orElseGet(ExchangeOrderEntity::new);

        e.setChatId(chatId);
        e.setExchange(exchange);
        e.setNetwork(network);

        e.setOrderId(resp.getOrderId());
        e.setSymbol(resp.getSymbol());

        // side/type в БД как строка
        e.setSide(req.getSide() == null ? null : req.getSide().name());
        e.setType(req.getType() == null ? null : req.getType().name());

        e.setPrice(req.getPrice());
        e.setQuantity(req.getQuantity());

        e.setExecutedQty(resp.getExecutedQty());
        e.setStatus(resp.getStatus() == null ? "NEW" : resp.getStatus());

        e.setCommission(resp.getCommission());
        e.setCommissionAsset(resp.getCommissionAsset());

        e.setPnl(pnl);
        e.setPnlPct(pnlPct);

        if (e.getCreatedAt() == null) {
            e.setCreatedAt(now);
        }
        e.setUpdatedAt(now);
        e.setLastCheckedAt(now);

        return repo.save(e);
    }

    /**
     * Открытые (NEW/PARTIALLY_FILLED) по чату и символу.
     */
    @Transactional(readOnly = true)
    public List<ExchangeOrderEntity> findOpenByChatAndSymbol(Long chatId, String symbol) {
        return repo.findByChatIdAndSymbolAndStatusIn(chatId, symbol, OPEN);
    }

    /**
     * Последние FILLED по дате обновления, ограничение по количеству.
     */
    @Transactional(readOnly = true)
    public List<ExchangeOrderEntity> findRecentFilled(Long chatId, String symbol, int limit) {
        int l = Math.max(1, limit);
        return repo.findByChatIdAndSymbolAndStatusOrderByUpdatedAtDesc(
                chatId, symbol, "FILLED", PageRequest.of(0, l)
        );
    }

    /**
     * Закрытые статусы для справки, если пригодится.
     */
    @Transactional(readOnly = true)
    public List<ExchangeOrderEntity> findClosedByChatAndSymbol(Long chatId, String symbol) {
        return repo.findByChatIdAndSymbolAndStatusIn(chatId, symbol, CLOSED);
    }
}
