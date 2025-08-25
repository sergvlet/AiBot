package com.chicu.aibot.bot.menu.feature.ai.strategy.bollinger.service;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

public interface BollingerPanelRenderer {
    SendMessage render(Long chatId);
}
