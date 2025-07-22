package com.chicu.aibot.bot.menu.feature.exchange;

import com.chicu.aibot.bot.menu.core.MenuSessionService;
import com.chicu.aibot.bot.menu.core.MenuState;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ExchangeApiKeyInputPublicState implements MenuState {
    public static final String NAME = "exchange_api_input_public";

    private final MenuSessionService session;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public SendMessage render(Long chatId) {
        return SendMessage.builder()
            .chatId(chatId.toString())
            .text("🔑 Пожалуйста, введите *публичный* API-ключ:")
            .parseMode("Markdown")
            .build();
    }

    @Override
    public String handleInput(Update update) {
        if (!update.hasMessage()) {
            return NAME;
        }
        Message msg = update.getMessage();
        String publicKey = msg.getText().trim();
        Long chatId = msg.getChatId();

        // Сохраняем в сессии только публичный, ждём секрет
        session.setNextValue(chatId, publicKey);
        return ExchangeApiKeyInputSecretState.NAME;
    }
}
