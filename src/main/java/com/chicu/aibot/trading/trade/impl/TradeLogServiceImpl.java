// src/main/java/com/chicu/aibot/trading/trade/impl/TradeLogServiceImpl.java
package com.chicu.aibot.trading.trade.impl;

import com.chicu.aibot.trading.trade.TradeLogEntity;
import com.chicu.aibot.trading.trade.TradeLogRepository;
import com.chicu.aibot.trading.trade.TradeLogService;
import com.chicu.aibot.trading.trade.model.TradeLogEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TradeLogServiceImpl implements TradeLogService {

    private final TradeLogRepository repo;

    @Override
    public Optional<TradeLogEntry> getLastTrade(Long chatId, String symbol) {
        return repo.findTopByChatIdAndSymbolOrderByCloseTimeDesc(chatId, symbol)
                .map(e -> TradeLogEntry.builder()
                        .chatId(e.getChatId())
                        .symbol(e.getSymbol())
                        .openTime(e.getOpenTime())
                        .closeTime(e.getCloseTime())
                        .entryPrice(e.getEntryPrice())
                        .exitPrice(e.getExitPrice())
                        .volume(e.getVolume())
                        .pnl(e.getPnl())
                        .pnlPct(e.getPnlPct())
                        .side(e.getSide())
                        .build());
    }

    @Override
    public void logTrade(TradeLogEntry entry) {
        TradeLogEntity entity = TradeLogEntity.builder()
                .chatId(entry.getChatId())
                .symbol(entry.getSymbol())
                .openTime(entry.getOpenTime())
                .closeTime(entry.getCloseTime())
                .entryPrice(entry.getEntryPrice())
                .exitPrice(entry.getExitPrice())
                .volume(entry.getVolume())
                .pnl(entry.getPnl())
                .pnlPct(entry.getPnlPct())
                .side(entry.getSide())
                .build();
        repo.save(entity);
    }

    @Override
    public Optional<Double> getTotalPnl(Long chatId, String symbol) {
        Double sum = repo.sumPnlByChatIdAndSymbol(chatId, symbol);
        return Optional.ofNullable(sum);
    }
}
