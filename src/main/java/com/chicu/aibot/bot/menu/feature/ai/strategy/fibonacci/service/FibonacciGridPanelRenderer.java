package com.chicu.aibot.bot.menu.feature.ai.strategy.fibonacci.service;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

public interface FibonacciGridPanelRenderer {
    SendMessage render(Long chatId);
}
