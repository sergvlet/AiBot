package com.chicu.aibot.bot.menu.feature.exchange;

import com.chicu.aibot.bot.menu.core.MenuSessionService;
import com.chicu.aibot.bot.menu.core.MenuState;
import com.chicu.aibot.exchange.service.ExchangeSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
public class ExchangeApiKeyInputSecretState implements MenuState {
    public static final String NAME = "exchange_api_input_secret";

    private final MenuSessionService session;
    private final ExchangeSettingsService exchangeService;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public SendMessage render(Long chatId) {
        return SendMessage.builder()
            .chatId(chatId.toString())
            .text("🔒 Пожалуйста, введите секретный ключ:")
            .build();
    }

    @Override
    public String handleInput(Update update) {
        if (!update.hasMessage()) return NAME;
        var msg = update.getMessage();
        var chatId = msg.getChatId();
        var secretKey = msg.getText().trim();

        var publicKey = session.getNextValue(chatId);
        session.clearNextValue(chatId);

        exchangeService.saveApiKeys(chatId, publicKey, secretKey);
        session.setCurrentState(chatId, ExchangeStatusState.NAME);

        boolean ok = exchangeService.testConnection(chatId);
        var text = ok
            ? "✅ Ключи успешно сохранены и соединение установлено."
            : "❌ Ключи сохранены, но подключиться не удалось.";

        return SendMessage.builder()
            .chatId(chatId.toString())
            .text(text)
            .build()
            .toString();
    }
}
