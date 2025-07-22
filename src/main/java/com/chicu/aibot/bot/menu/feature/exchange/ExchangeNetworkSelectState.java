package com.chicu.aibot.bot.menu.feature.exchange;

import com.chicu.aibot.bot.menu.core.MenuState;
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
public class ExchangeNetworkSelectState implements MenuState {
    public static final String NAME = "exchange_network_select";
    private final ExchangeSettingsService exchangeService;

    @Override
    public String name() { return NAME; }

    @Override
    public SendMessage render(Long chatId) {
        ExchangeSettings s = exchangeService.getOrCreate(chatId);
        var rows = List.of(
            List.of(
                InlineKeyboardButton.builder().text("🔗 Mainnet").callbackData("network:MAINNET").build(),
                InlineKeyboardButton.builder().text("🔗 Testnet").callbackData("network:TESTNET").build()
            ),
            List.of(
                InlineKeyboardButton.builder().text("‹ Назад").callbackData(ExchangeSelectState.NAME).build()
            )
        );
        return SendMessage.builder()
            .chatId(chatId.toString())
            .text(String.format("*Выбор сети*\nТекущая: `%s`\n\nВыберите:", s.getNetwork()))
            .parseMode("Markdown")
            .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
            .build();
    }

    @Override
    public String handleInput(Update update) {
        var cq = update.getCallbackQuery();
        if (cq == null) return NAME;
        var chatId = cq.getMessage().getChatId();
        var data = cq.getData();
        if (data.startsWith("network:")) {
            exchangeService.updateNetwork(chatId, data.substring(8));
            return ExchangeStatusState.NAME;
        }
        if (ExchangeSelectState.NAME.equals(data)) {
            return ExchangeSelectState.NAME;
        }
        return NAME;
    }
}
