package com.chicu.aibot.bot.menu.feature.ai.strategy.fibonacciGrid.service;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

public interface FibonacciGridPanelRenderer {
    SendMessage render(Long chatId);
}
