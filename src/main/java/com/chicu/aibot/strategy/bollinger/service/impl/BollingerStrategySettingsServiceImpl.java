package com.chicu.aibot.strategy.bollinger.service.impl;

import com.chicu.aibot.strategy.bollinger.model.BollingerStrategySettings;
import com.chicu.aibot.strategy.bollinger.repository.BollingerStrategySettingsRepository;
import com.chicu.aibot.strategy.bollinger.service.BollingerStrategySettingsService;
import com.chicu.aibot.strategy.common.DefaultTradingParamsResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BollingerStrategySettingsServiceImpl implements BollingerStrategySettingsService {

    private final BollingerStrategySettingsRepository repo;
    private final DefaultTradingParamsResolver defaults;

    @Value("${trading.defaults.symbol:BTCUSDT}")
    private String defaultSymbolProp;

    @Value("${trading.defaults.timeframe.bollinger:1h}")
    private String defaultTfProp;

    @Override
    @Transactional
    public BollingerStrategySettings getOrCreate(Long chatId) {
        return repo.findById(chatId).orElseGet(() -> {
            final String symbol    = defaults.resolveSymbol(chatId, defaultSymbolProp, "BTCUSDT");
            final String timeframe = defaults.resolveTimeframe(chatId, defaultTfProp, "1h");

            log.info("⚙️ Не найдены настройки Bollinger для chatId={}, создаю по умолчанию (symbol={}, tf={})",
                    chatId, symbol, timeframe);

            BollingerStrategySettings def = getBollingerStrategySettings(chatId, symbol, timeframe);

            return repo.saveAndFlush(def);
        });
    }

    @NotNull
    private static BollingerStrategySettings getBollingerStrategySettings(Long chatId, String symbol, String timeframe) {
        BollingerStrategySettings def = new BollingerStrategySettings();
        def.setChatId(chatId);
        def.setSymbol(symbol);
        def.setTimeframe(timeframe);

        def.setCachedCandlesLimit(520);
        def.setOrderVolume(1.0);

        def.setPeriod(20);
        def.setStdDevMultiplier(2.0);   // ✅ ОБЯЗАТЕЛЬНО: заполнить std_dev_mult

        def.setTakeProfitPct(1.0);
        def.setStopLossPct(0.5);

        def.setAllowLong(true);
        def.setAllowShort(false);

        def.setActive(false);
        return def;
    }

    @Override
    @Transactional
    public BollingerStrategySettings save(BollingerStrategySettings settings) {
        // подстраховка на случай, если кто-то где-то обнулил σ
        if (settings.getStdDevMultiplier() == null) {
            settings.setStdDevMultiplier(2.0);
        }
        log.info("💾 Сохраняю настройки Bollinger для chatId={}", settings.getChatId());
        return repo.saveAndFlush(settings);
    }
}
