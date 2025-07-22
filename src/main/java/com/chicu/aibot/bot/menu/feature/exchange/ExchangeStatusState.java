package com.chicu.aibot.bot.menu.feature.exchange;

import com.chicu.aibot.bot.menu.core.MenuService;
import com.chicu.aibot.bot.menu.core.MenuState;
import com.chicu.aibot.bot.menu.core.MenuSessionService;
import com.chicu.aibot.exchange.model.ExchangeSettings;
import com.chicu.aibot.exchange.service.ExchangeSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ExchangeStatusState implements MenuState {
    public static final String NAME = "exchange_status";
    private final ExchangeSettingsService exchangeService;
    private final MenuSessionService session;

    @Override
    public String name() { return NAME; }

    @Override
    public SendMessage render(Long chatId) {
        ExchangeSettings s = exchangeService.getOrCreate(chatId);
        boolean hasKeys = exchangeService.hasApiKeys(chatId);
        boolean connected = hasKeys && exchangeService.testConnection(chatId);

        String text = String.format(
            "*%s* (%s)\n\n" +
            "API ключи: %s\n" +
            "Соединение: %s",
            s.getExchange(),
            s.getNetwork(),
            hasKeys ? "🔑 Сохранены" : "❌ Нет ключей",
            connected ? "✅ OK" : "❌ Не подключён"
        );

        var rows = List.<List<InlineKeyboardButton>>of(
            List.of(
                InlineKeyboardButton.builder()
                    .text(hasKeys ? "✏️ Изменить ключи" : "🔑 Ввести ключи")
                    .callbackData("exchange_api_input_public")
                    .build()
            ),
            List.of(
                InlineKeyboardButton.builder().text("‹ Назад").callbackData(MenuService.MAIN_MENU).build()
            )
        );

        return SendMessage.builder()
            .chatId(chatId.toString())
            .parseMode("Markdown")
            .text(text)
            .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
            .build();
    }

    @Override
    public String handleInput(Update update) {
        var cq = update.getCallbackQuery();
        if (cq == null) return NAME;
        String data = cq.getData();
        if ("exchange_api_input_public".equals(data)) {
            session.setNextValue(cq.getMessage().getChatId(), "EXCHANGE_PUBLIC_KEY");
            return ExchangeApiKeyInputPublicState.NAME;
        }
        if (MenuService.MAIN_MENU.equals(data)) {
            return MenuService.MAIN_MENU;
        }
        return NAME;
    }
}
