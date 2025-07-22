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
    public String name() {
        return NAME;
    }

    @Override
    public SendMessage render(Long chatId) {
        // берём текущую настройку
        ExchangeSettings settings = exchangeService.getOrCreate(chatId);
        String currentNetwork = settings.getNetwork().name();

        var rows = List.of(
                List.of(
                        InlineKeyboardButton.builder()
                                .text("🔗 Mainnet")
                                .callbackData("network:MAINNET")
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("🔗 Testnet")
                                .callbackData("network:TESTNET")
                                .build()
                ),
                List.of(
                        InlineKeyboardButton.builder()
                                .text("‹ Назад")
                                .callbackData(ExchangeSelectState.NAME)  // возвращаемся к выбору биржи
                                .build()
                )
        );

        return SendMessage.builder()
                .chatId(chatId.toString())
                .parseMode("Markdown")
                .text(
                        "*Выбор сети*\n\n" +
                                "Текущая сеть: `" + currentNetwork + "`\n\n" +
                                "Пожалуйста, выберите сеть:"
                )
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                .build();
    }

    @Override
    public String handleInput(Update update) {
        var cq = update.getCallbackQuery();
        if (cq == null) {
            return NAME;
        }

        String data = cq.getData();
        Long chatId = cq.getMessage().getChatId();

        if (data.startsWith("network:")) {
            String net = data.substring("network:".length());
            exchangeService.updateNetwork(chatId, net);
            return ExchangeStatusState.NAME;
        }

        // кнопка «‹ Назад» возвращает в выбор биржи
        if (ExchangeSelectState.NAME.equals(data)) {
            return ExchangeSelectState.NAME;
        }

        return NAME;
    }
}
