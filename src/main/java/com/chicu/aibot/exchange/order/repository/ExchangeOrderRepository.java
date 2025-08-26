package com.chicu.aibot.exchange.order.repository;

import com.chicu.aibot.exchange.enums.NetworkType;
import com.chicu.aibot.exchange.order.model.ExchangeOrderEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ExchangeOrderRepository extends JpaRepository<ExchangeOrderEntity, Long> {

    Optional<ExchangeOrderEntity> findByChatIdAndExchangeAndNetworkAndOrderId(
            Long chatId, String exchange, NetworkType network, String orderId);

    List<ExchangeOrderEntity> findByChatIdAndSymbolAndStatusIn(Long chatId, String symbol, Set<String> statuses);

    List<ExchangeOrderEntity> findByChatIdAndSymbolAndSideAndStatusIn(Long chatId, String symbol, String side, Set<String> statuses);

    List<ExchangeOrderEntity> findByStatusIn(Set<String> statuses);

    List<ExchangeOrderEntity> findByChatIdAndSymbolAndStatusOrderByUpdatedAtDesc(
            Long chatId, String symbol, String status, Pageable pageable);
}
