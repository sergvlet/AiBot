package com.chicu.aibot.bot.menu.feature.ai.strategy.scalping.service;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

public interface ScalpingPanelRenderer {
    SendMessage render(Long chatId);
}
