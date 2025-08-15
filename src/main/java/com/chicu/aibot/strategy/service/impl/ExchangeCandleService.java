package com.chicu.aibot.strategy.service.impl;

import com.chicu.aibot.exchange.client.ExchangeClient;
import com.chicu.aibot.exchange.client.ExchangeClientFactory;
import com.chicu.aibot.exchange.model.ExchangeApiKey;
import com.chicu.aibot.exchange.model.ExchangeSettings;
import com.chicu.aibot.exchange.service.ExchangeSettingsService;
import com.chicu.aibot.strategy.model.Candle;
import com.chicu.aibot.strategy.service.CandleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeCandleService implements CandleService {

    private final ExchangeClientFactory clientFactory;
    private final ExchangeSettingsService settingsService;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Candle> getCandles(Long chatId, String symbol, String timeframe, int limit) {
        log.info("Запрос исторических свечей: chatId={} symbol={} timeframe={} limit={}",
                 chatId, symbol, timeframe, limit);

        // 1) Получаем настройки биржи для данного чата (где хранятся публичный/секретный ключи и сеть)
        ExchangeSettings settings = settingsService.getOrCreate(chatId);
        ExchangeApiKey   keys      = settingsService.getApiKey(chatId);

        // 2) Берём нужный клиент (Binance, Bybit и т. д.)
        ExchangeClient client = clientFactory.getClient(settings.getExchange());

        // 3) Делаем запрос исторических свечей (klines) через клиента
        List<Candle> candles = client.fetchCandles(
            keys.getPublicKey(),
            keys.getSecretKey(),
            settings.getNetwork(),
            symbol,
            timeframe,
            limit
        );

        log.debug("Получено {} свечей для {} {}", candles.size(), symbol, timeframe);
        return candles;
    }
}
