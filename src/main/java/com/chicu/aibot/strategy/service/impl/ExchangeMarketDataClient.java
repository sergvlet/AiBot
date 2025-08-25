package com.chicu.aibot.strategy.service.impl;

import com.chicu.aibot.exchange.client.ExchangeClient;
import com.chicu.aibot.exchange.client.ExchangeClientFactory;
import com.chicu.aibot.exchange.model.ExchangeSettings;
import com.chicu.aibot.exchange.model.TickerInfo;
import com.chicu.aibot.exchange.service.ExchangeSettingsService;
import com.chicu.aibot.strategy.service.MarketDataClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Реализация {@link MarketDataClient} через ExchangeClient.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeMarketDataClient implements MarketDataClient {

    private final ExchangeClientFactory clientFactory;
    private final ExchangeSettingsService settingsService;

    @Override
    public double getLastPrice(Long chatId, String symbol) {
        // получаем настройки (биржа, сеть, ключи)
        ExchangeSettings settings = settingsService.getOrCreate(chatId);
        // создаём конкретный клиент
        ExchangeClient client = clientFactory.getClient(settings.getExchange());

        // запросим тикер
        Optional<TickerInfo> info = client.getTicker(symbol, settings.getNetwork());
        BigDecimal priceBd = info.get().getPrice();
        double price = priceBd != null ? priceBd.doubleValue() : 0.0;

        log.info("Текущая цена {} (биржа={}, сеть={}): {}", 
                 symbol, settings.getExchange(), settings.getNetwork(), price);
        return price;
    }
}
