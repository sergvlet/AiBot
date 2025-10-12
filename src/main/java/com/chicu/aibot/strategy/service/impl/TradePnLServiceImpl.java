// src/main/java/com/chicu/aibot/strategy/service/impl/TradePnLServiceImpl.java
package com.chicu.aibot.strategy.service.impl;

import com.chicu.aibot.strategy.service.PnLReport;
import com.chicu.aibot.strategy.service.TradePnLService;
import com.chicu.aibot.trading.trade.TradeLogService;
import com.chicu.aibot.trading.trade.model.TradeLogEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TradePnLServiceImpl implements TradePnLService {

    private final TradeLogService tradeLogService;

    /**
     * Собираем краткий отчёт: суммарный реализованный PnL + последняя сделка (если есть).
     * Интерфейса для списка сделок сейчас нет, поэтому показываем только последнюю.
     */
    @Override
    public PnLReport getRecent(Long chatId, String symbol, int lastDealsLimit) {
        // Суммарный PnL
        BigDecimal realized = tradeLogService
                .getTotalPnl(chatId, symbol)      // Optional<Double>
                .map(BigDecimal::valueOf)
                .orElse(BigDecimal.ZERO);

        // Последняя сделка → превращаем в Deal
        List<PnLReport.Deal> deals = tradeLogService
                .getLastTrade(chatId, symbol)     // Optional<TradeLogEntry>
                .map(TradePnLServiceImpl::toDeal)
                .map(List::of)
                .orElse(List.of());

        return PnLReport.builder()
                .realizedPnl(realized)
                .deals(deals)
                .build();
    }

    private static PnLReport.Deal toDeal(TradeLogEntry last) {
        BigDecimal price = firstNonNull(last.getExitPrice(), last.getEntryPrice(), BigDecimal.ZERO);
        BigDecimal qty   = firstNonNull(last.getVolume(), BigDecimal.ZERO);
        BigDecimal pnl   = firstNonNull(last.getPnl(), BigDecimal.ZERO);

        return PnLReport.Deal.builder()
                .time(last.getCloseTime() != null ? last.getCloseTime() : last.getOpenTime())
                .side(nvl(last.getSide()))
                .price(price)
                .qty(qty)
                .pnl(pnl)
                .orderId(null) // если появится айди ордера в TradeLogEntry — подставим
                .build();
    }

    private static String nvl(String s) { return s == null ? "" : s; }

    @SafeVarargs
    private static <T> T firstNonNull(T... vals) {
        for (T v : vals) if (v != null) return v;
        return null;
    }
}
