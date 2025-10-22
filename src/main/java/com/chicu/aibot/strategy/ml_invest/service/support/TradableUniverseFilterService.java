package com.chicu.aibot.strategy.ml_invest.service.support;

import com.chicu.aibot.exchange.model.ExchangeSettings;
import com.chicu.aibot.exchange.service.ExchangeSettingsService;
import com.chicu.aibot.strategy.service.CandleService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Фильтрует список символов по их реальной доступности на выбранной бирже/сети.
 * Критерий: если по символу возвращается >=1 свеча (limit=1), считаем его доступным.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradableUniverseFilterService {

    private final CandleService candleService;
    private final ExchangeSettingsService settingsService;

    public List<String> filterByExchangeAvailability(@NonNull Long chatId,
                                                     @NonNull String timeframe,
                                                     @NonNull List<String> symbols) {
        if (symbols.isEmpty()) return symbols;

        ExchangeSettings s = settingsService.getOrCreate(chatId);
        log.info("[ML] universe-filter: exchange={} network={} candidates={}",
                s.getExchange(), s.getNetwork(), symbols.size());

        List<String> ok = new ArrayList<>(symbols.size());
        for (String sym : symbols) {
            try {
                var candles = candleService.getCandles(chatId, sym, timeframe, 1);
                if (candles != null && !candles.isEmpty()) {
                    ok.add(sym);
                } else {
                    log.warn("[ML] universe-filter: {} исключён — нет свечей ({})", sym, timeframe);
                }
            } catch (Exception ex) {
                log.warn("[ML] universe-filter: {} исключён — ошибка свечей: {}", sym, ex.getMessage());
            }
        }
        log.info("[ML] universe-filter: итог {} → {}", symbols.size(), ok.size());
        return ok;
    }
}
