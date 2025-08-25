package com.chicu.aibot.trading.trade;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface TradeLogRepository extends JpaRepository<TradeLogEntity, Long> {

    Optional<TradeLogEntity> findTopByChatIdAndSymbolOrderByCloseTimeDesc(Long chatId, String symbol);

    @Query("SELECT SUM(t.pnl) FROM TradeLogEntity t WHERE t.chatId = :chatId AND t.symbol = :symbol")
    Double sumPnlByChatIdAndSymbol(Long chatId, String symbol);
}
