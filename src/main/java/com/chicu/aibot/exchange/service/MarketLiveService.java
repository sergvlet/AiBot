package com.chicu.aibot.exchange.service;

import com.chicu.aibot.bot.menu.feature.ai.strategy.view.LiveSnapshot;

public interface MarketLiveService {
    LiveSnapshot build(Long chatId, String symbol);
}
