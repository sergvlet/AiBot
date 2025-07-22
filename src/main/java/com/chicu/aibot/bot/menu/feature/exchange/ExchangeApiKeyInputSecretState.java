package com.chicu.aibot.bot.menu.feature.exchange;

import com.chicu.aibot.bot.menu.core.MenuSessionService;
import com.chicu.aibot.bot.menu.core.MenuState;
import com.chicu.aibot.exchange.model.ExchangeSettings;
import com.chicu.aibot.exchange.service.ExchangeSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
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
        ExchangeSettings settings = exchangeService.getOrCreate(chatId);
        return SendMessage.builder()
                .chatId(chatId.toString())
                .text("🔒 Пожалуйста, введите *секретный* API-ключ для сети `"
                        + settings.getNetwork() + "`:")
                .parseMode("Markdown")
                .build();
    }

    @Override
    public String handleInput(Update update) {
        if (!update.hasMessage()) {
            return NAME;
        }
        Message msg = update.getMessage();
        Long chatId = msg.getChatId();
        String secretKey = msg.getText().trim();

        // Берём публичный из сессии
        String publicKey = session.getNextValue(chatId);

        // Сохраняем оба ключа
        exchangeService.saveApiKeys(chatId, publicKey, secretKey);

        // Очищаем сессию
        session.clearNextValue(chatId);

        boolean ok = exchangeService.testConnection(chatId);
        String text = ok
            ? "✅ Ключи сохранены и соединение установлено успешно!"
            : "❌ Ключи сохранены, но не удалось подключиться.";

        return String.valueOf(SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .build());
    }
}
