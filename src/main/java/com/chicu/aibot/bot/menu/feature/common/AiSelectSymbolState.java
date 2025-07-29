package com.chicu.aibot.bot.menu.feature.common;

import com.chicu.aibot.bot.menu.core.MenuSessionService;
import com.chicu.aibot.bot.menu.core.MenuState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AiSelectSymbolState implements MenuState {

    public static final String NAME = "ai_select_symbol";

    private final MenuSessionService sessionService;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public SendMessage render(Long chatId) {
        String backState = sessionService.getReturnState(chatId);
        if (backState == null) backState = "ai_trading";

        String text = "*Выбор пары*\n\nВыберите, как вы хотите подобрать символ:";

        List<List<InlineKeyboardButton>> rows = List.of(
                List.of(
                        button("🔥 Популярные пары", "symbol_popular"),
                        button("📈 Показывают рост", "symbol_gainers")
                ),
                List.of(
                        button("📉 Теряют в цене", "symbol_losers"),
                        button("💰 Макс. объем", "symbol_volume")
                ),
                List.of(
                        button("‹ Назад", backState)
                )
        );

        return SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode("Markdown")
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                .build();
    }

    @Override
    public String handleInput(Update update) {
        String data = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();

        // Заглушки — пока не реализовано поведение выбора
        if (data.startsWith("symbol_")) {
            // TODO: Реализовать выбор символа
            return NAME;
        }

        if (data.equals("ai_trading") || data.startsWith("ai_trading_")) {
            return data;
        }

        return NAME;
    }

    private InlineKeyboardButton button(String text, String data) {
        return InlineKeyboardButton.builder().text(text).callbackData(data).build();
    }
}
