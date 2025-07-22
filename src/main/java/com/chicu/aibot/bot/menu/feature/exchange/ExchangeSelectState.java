package com.chicu.aibot.bot.menu.feature.exchange;

import com.chicu.aibot.bot.menu.core.MenuService;
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
public class ExchangeSelectState implements MenuState {
    public static final String NAME = "exchange_select";
    private final ExchangeSettingsService exchangeService;

    @Override
    public String name() { return NAME; }

    @Override
    public SendMessage render(Long chatId) {
        ExchangeSettings s = exchangeService.getOrCreate(chatId);
        var rows = List.of(
            List.of(
                InlineKeyboardButton.builder().text("🏦 Binance").callbackData("exchange:BINANCE").build(),
                InlineKeyboardButton.builder().text("🏦 Bybit").callbackData("exchange:BYBIT").build(),
                InlineKeyboardButton.builder().text("🏦 Coinbase").callbackData("exchange:COINBASE").build()
            ),
            List.of(
                InlineKeyboardButton.builder().text("‹ Назад").callbackData(MenuService.MAIN_MENU).build()
            )
        );
        return SendMessage.builder()
            .chatId(chatId.toString())
            .text(String.format("*Выбор биржи*\nТекущая: `%s`\n\nВыберите:", s.getExchange()))
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
        if (data.startsWith("exchange:")) {
            exchangeService.updateExchange(chatId, data.substring(9));
            return ExchangeNetworkSelectState.NAME;
        }
        if (MenuService.MAIN_MENU.equals(data)) {
            return MenuService.MAIN_MENU;
        }
        return NAME;
    }
}
