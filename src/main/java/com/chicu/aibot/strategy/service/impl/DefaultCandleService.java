// src/main/java/com/chicu/aibot/bot/strategy/service/impl/DefaultCandleService.java
package com.chicu.aibot.strategy.service.impl;

import com.chicu.aibot.strategy.model.Candle;
import com.chicu.aibot.strategy.service.CandleService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Заглушка: возвращает пустой список.
 * Позже замените на реальный вызов API биржи.
 */
@Service
public class DefaultCandleService implements CandleService {
    @Override
    public List<Candle> getLastCandles(String symbol, String timeframe, int limit) {
        return Collections.emptyList();
    }
}
