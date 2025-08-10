package com.chicu.aibot.strategy.scalping.service.impl;

import com.chicu.aibot.strategy.common.DefaultTradingParamsResolver;
import com.chicu.aibot.strategy.scalping.model.ScalpingStrategySettings;
import com.chicu.aibot.strategy.scalping.repository.ScalpingStrategySettingsRepository;
import com.chicu.aibot.strategy.scalping.service.ScalpingStrategySettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScalpingStrategySettingsServiceImpl implements ScalpingStrategySettingsService {

    private final ScalpingStrategySettingsRepository repo;
    private final DefaultTradingParamsResolver defaults;

    @Value("${trading.defaults.symbol:BTCUSDT}")
    private String defaultSymbolProp;

    @Value("${trading.defaults.timeframe.scalping:1m}")
    private String defaultTfProp;

    @Override
    @Transactional
    public ScalpingStrategySettings getOrCreate(Long chatId) {
        return repo.findById(chatId).orElseGet(() -> {
            String symbol    = defaults.resolveSymbol(chatId, defaultSymbolProp, "BTCUSDT");
            String timeframe = defaults.resolveTimeframe(chatId, defaultTfProp, "1m");

            log.info("⚙️ Не найдены настройки Scalping для chatId={}, создаю по умолчанию (symbol={}, tf={})",
                    chatId, symbol, timeframe);

            // Дефолты под текущую типичную волатильность BTCUSDT на 1m
            ScalpingStrategySettings def = ScalpingStrategySettings.builder()
                    .chatId(chatId)
                    .symbol(symbol)              // BTCUSDT из application.yml можно переопределить
                    .timeframe(timeframe)        // 1m
                    .cachedCandlesLimit(300)     // истории достаточно
                    .windowSize(20)              // окно для расчёта импульса
                    .orderVolume(0.001)          // 0.001 BTC ~ “микро-лото”
                    .priceChangeThreshold(0.15)  // вход при |Δцены| ≥ 0.15%
                    .spreadThreshold(0.03)       // фильтр по спреду ≤ 0.03%
                    .takeProfitPct(0.25)         // цель 0.25%
                    .stopLossPct(0.20)           // SL 0.20%
                    .active(false)               // по умолчанию выключено
                    .build();

            return repo.saveAndFlush(def);
        });
    }

    @Override
    @Transactional
    public void save(ScalpingStrategySettings settings) {
        log.info("💾 Сохраняю настройки Scalping для chatId={}", settings.getChatId());
        repo.saveAndFlush(settings);
    }
}
