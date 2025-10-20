package com.chicu.aibot.bot.menu.feature.ai.strategy.ml_invest.service;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

public interface MlInvestPanelRenderer {
    SendMessage render(Long chatId);
}
