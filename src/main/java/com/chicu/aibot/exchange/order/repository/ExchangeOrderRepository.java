package com.chicu.aibot.exchange.order.repository;

import com.chicu.aibot.exchange.enums.NetworkType;
import com.chicu.aibot.exchange.enums.OrderSide;
import com.chicu.aibot.exchange.order.model.ExchangeOrderEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface ExchangeOrderRepository extends JpaRepository<ExchangeOrderEntity, Long> {

    // --- нужно для OrderExecutionService (анти-дубликаты) ---
    Optional<ExchangeOrderEntity> findTopByChatIdAndExchangeAndNetworkAndSymbolAndSideAndTypeAndPriceAndQuantityAndStatusInOrderByCreatedAtDesc(
            Long chatId, String exchange, NetworkType network, String symbol, String side, String type, BigDecimal price, BigDecimal quantity, Collection<String> status
            // <- используем enum, как в сущности
            // <- если есть свой enum OrderType — замени на него
    );

    // --- нужно для ExchangeOrderDbService: поиск по бизнес-ключу ---
    Optional<ExchangeOrderEntity> findByChatIdAndExchangeAndNetworkAndOrderId(
            Long chatId,
            String exchange,
            NetworkType network,
            String orderId
    );

    // --- нужно для ExchangeOrderDbService: список по статусам ---
    List<ExchangeOrderEntity> findByChatIdAndSymbolAndStatusIn(
            Long chatId,
            String symbol,
            Set<String> statuses
    );

    // --- нужно для ExchangeOrderDbService: последние по статусу (c пагинацией) ---
    List<ExchangeOrderEntity> findByChatIdAndSymbolAndStatusOrderByUpdatedAtDesc(
            Long chatId,
            String symbol,
            String status,
            Pageable pageable
    );

    // Часто используемая выборка по одному статусу
    List<ExchangeOrderEntity> findByChatIdAndSymbolAndStatus(
            Long chatId,
            String symbol,
            String status
    );

    // Подсчёт активных по стороне
    default long countByChatIdAndSymbolAndStatusAndSide(
            Long chatId,
            String symbol,
            String status,
            OrderSide side
    ) {
        return 0;
    }
}
