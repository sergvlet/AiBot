package com.chicu.aibot.strategy.ml_invest.service.impl;

import com.chicu.aibot.strategy.ml_invest.service.MlDataPipelineService;
import com.chicu.aibot.strategy.ml_invest.service.support.TradableUniverseFilterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Прокси-слой поверх существующего MlDataPipelineServiceImpl:
 * - фильтрует "вселенную" от недоступных символов (testnet/прочее)
 * - остальное делегирует 1-в-1 в оригинальный сервис
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class MlDataPipelineServiceFilteringProxy implements MlDataPipelineService {

    private final MlDataPipelineServiceImpl delegate;                    // твоя исходная реализация
    private final TradableUniverseFilterService universeFilterService;   // наш новый фильтр

    @Override
    public List<String> pickUniverse(Long chatId, String timeframe, int universeSize, BigDecimal min24hQuoteVolume) {
        List<String> raw = delegate.pickUniverse(chatId, timeframe, universeSize, min24hQuoteVolume);
        List<String> filtered = universeFilterService.filterByExchangeAvailability(chatId, timeframe, raw);
        log.info("[ML] pickUniverse (filtered) chatId={} -> {} (size={})", chatId, filtered, filtered.size());
        return filtered;
    }

    @Override
    public Dataset buildTrainingDataset(Long chatId, List<String> symbols, String timeframe, int windowDays) {
        return delegate.buildTrainingDataset(chatId, symbols, timeframe, windowDays);
    }

    @Override
    public String trainIfNeeded(Long chatId, Dataset dataset, Duration retrainIfOlderThan, boolean force) {
        return delegate.trainIfNeeded(chatId, dataset, retrainIfOlderThan, force);
    }

    @Override
    public Map<String, Probabilities> predict(Long chatId, String modelRef, List<String> symbols, String timeframe) {
        return delegate.predict(chatId, modelRef, symbols, timeframe);
    }
}
