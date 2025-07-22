package com.chicu.aibot.bot.menu.feature.exchange;

import com.chicu.aibot.bot.menu.core.MenuSessionService;
import com.chicu.aibot.bot.menu.core.MenuState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
public class ExchangeApiKeyInputPublicState implements MenuState {
    public static final String NAME = "exchange_api_input_public";

    private final MenuSessionService session;

    @Override
    public String name() { return NAME; }

    @Override
    public SendMessage render(Long chatId) {
        return SendMessage.builder()
            .chatId(chatId.toString())
            .text("🔑 Пожалуйста, введите публичный API-ключ:")
            .build();
    }

    @Override
    public String handleInput(Update update) {
        if (!update.hasMessage()) return NAME;
        var msg = update.getMessage();
        var chatId = msg.getChatId();
        var publicKey = msg.getText().trim();

        session.setNextValue(chatId, publicKey);
        session.setCurrentState(chatId, ExchangeApiKeyInputSecretState.NAME);
        return ExchangeApiKeyInputSecretState.NAME;
    }
}
