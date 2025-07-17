package com.chicu.aibot.bot.strategy.fibonacci.service.impl;

import com.chicu.aibot.bot.strategy.fibonacci.model.FibonacciGridStrategySettings;
import com.chicu.aibot.bot.strategy.fibonacci.repository.FibonacciGridStrategySettingsRepository;
import com.chicu.aibot.bot.strategy.fibonacci.service.FibonacciGridStrategySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FibonacciGridStrategySettingsServiceImpl
        implements FibonacciGridStrategySettingsService {

    private final FibonacciGridStrategySettingsRepository repo;

    @Override
    @Transactional
    public FibonacciGridStrategySettings getOrCreate(Long chatId) {
        return repo.findById(chatId).orElseGet(() -> {
            // дефолтные параметры стратегии
            FibonacciGridStrategySettings def = FibonacciGridStrategySettings.builder()
                    .chatId(chatId)
                    // пример символа, можно заменить на пустую строку или взять из общих настроек
                    .symbol("BTCUSDT")
                    // стандартные уровни Фибоначчи
                    .levels(List.of(0.382, 0.5, 0.618))
                    // шаг сетки в процентах
                    .gridSizePct(1.0)
                    // объём на ордер (в базовой валюте)
                    .orderVolume(0.001)
                    // максимальное число одновременно открытых ордеров
                    .maxActiveOrders(5)
                    // общая цель по прибыли в процентах
                    .takeProfitPct(2.0)
                    // стоп-лосс в процентах
                    .stopLossPct(1.0)
                    // разрешить длинные позиции
                    .allowLong(true)
                    // не разрешать шорты
                    .allowShort(false)
                    // таймфрейм
                    .timeframe("1h")
                    // сколько свечей грузить из API
                    .cachedCandlesLimit(100)
                    .build();
            return repo.saveAndFlush(def);
        });
    }

    @Override
    @Transactional
    public void save(FibonacciGridStrategySettings settings) {
        repo.saveAndFlush(settings);
    }
}
