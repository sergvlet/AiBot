package com.chicu.aibot.bot.menu.feature.ai.strategy.scalping.service;

import com.chicu.aibot.bot.menu.feature.ai.strategy.view.LiveSnapshot;

public interface ScalpingLiveService {
    LiveSnapshot build(Long chatId, String symbol);
}
