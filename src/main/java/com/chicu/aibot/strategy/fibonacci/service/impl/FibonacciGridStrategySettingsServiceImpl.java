package com.chicu.aibot.strategy.fibonacci.service.impl;

import com.chicu.aibot.strategy.common.DefaultTradingParamsResolver;
import com.chicu.aibot.strategy.fibonacci.model.FibonacciGridStrategySettings;
import com.chicu.aibot.strategy.fibonacci.repository.FibonacciGridStrategySettingsRepository;
import com.chicu.aibot.strategy.fibonacci.service.FibonacciGridStrategySettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FibonacciGridStrategySettingsServiceImpl implements FibonacciGridStrategySettingsService {

    private final FibonacciGridStrategySettingsRepository repo;
    private final DefaultTradingParamsResolver defaults;

    @Value("${trading.defaults.symbol:BTCUSDT}")
    private String defaultSymbolProp;

    @Value("${trading.defaults.timeframe.fibonacci:1h}")
    private String defaultTfProp;

    @Override
    @Transactional
    public FibonacciGridStrategySettings getOrCreate(Long chatId) {
        return repo.findById(chatId).orElseGet(() -> {
            String symbol    = defaults.resolveSymbol(chatId, defaultSymbolProp, "BTCUSDT");
            String timeframe = defaults.resolveTimeframe(chatId, defaultTfProp, "1h");

            log.info("⚙️ Не найдены настройки FibonacciGrid для chatId={}, создаю по умолчанию (symbol={}, tf={})",
                    chatId, symbol, timeframe);

            FibonacciGridStrategySettings def = FibonacciGridStrategySettings.builder()
                    .chatId(chatId)
                    .symbol(symbol)
                    .levels(List.of(0.382, 0.5, 0.618))
                    .gridSizePct(1.0)
                    .orderVolume(1.0)
                    .maxActiveOrders(5)
                    .takeProfitPct(2.0)
                    .stopLossPct(1.0)
                    .allowLong(true)
                    .allowShort(false)
                    .timeframe(timeframe)
                    .cachedCandlesLimit(100)
                    .active(false)
                    .build();
            return repo.saveAndFlush(def);
        });
    }

    @Override
    @Transactional
    public void save(FibonacciGridStrategySettings settings) {
        log.info("💾 Сохраняю настройки FibonacciGrid для chatId={}", settings.getChatId());
        repo.saveAndFlush(settings);

    }
}
