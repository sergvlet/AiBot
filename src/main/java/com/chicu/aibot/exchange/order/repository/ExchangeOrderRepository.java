package com.chicu.aibot.exchange.order.repository;

import com.chicu.aibot.exchange.order.model.ExchangeOrderEntity;
import com.chicu.aibot.exchange.enums.NetworkType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExchangeOrderRepository extends JpaRepository<ExchangeOrderEntity, Long> {

    Optional<ExchangeOrderEntity> findByChatIdAndExchangeAndNetworkAndOrderId(
            Long chatId, String exchange, NetworkType network, String orderId);

    List<ExchangeOrderEntity> findByChatIdAndSymbolAndStatusIn(
            Long chatId, String symbol, Collection<String> statuses);

    List<ExchangeOrderEntity> findByChatIdAndSymbolAndSideAndStatusIn(
            Long chatId, String symbol, String side, Collection<String> statuses);

    @Query("""
        select eo from ExchangeOrderEntity eo
        where eo.chatId = :chatId and eo.symbol = :symbol and eo.side = :side
          and eo.status in :statuses and (eo.price is null or eo.price = :price)
    """)
    List<ExchangeOrderEntity> findOpenByPrice(@Param("chatId") Long chatId,
                                              @Param("symbol") String symbol,
                                              @Param("side") String side,
                                              @Param("price") java.math.BigDecimal price,
                                              @Param("statuses") Collection<String> statuses);

    List<ExchangeOrderEntity> findByStatusIn(Collection<String> statuses);

    // ➕ Новое: последние FILLED-сделки (оба направления), с пагинацией
    List<ExchangeOrderEntity> findByChatIdAndSymbolAndStatusOrderByUpdatedAtDesc(
            Long chatId, String symbol, String status, Pageable pageable);
}
