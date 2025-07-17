package com.chicu.aibot.bot.menu.feature.manual;

import com.chicu.aibot.bot.menu.core.MenuService;
import com.chicu.aibot.bot.menu.core.MenuState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

@Slf4j
@Component
public class ManualOrdersState implements MenuState {

    private final InlineKeyboardMarkup keyboard;

    public ManualOrdersState() {
        this.keyboard = InlineKeyboardMarkup.builder()
            .keyboard(List.of(
                // Заглушка: здесь может быть список ордеров
                List.of(
                    InlineKeyboardButton.builder()
                        .text("⬅️ Назад")
                        .callbackData("manual_trading_settings")
                        .build()
                )
            )).build();
    }

    @Override
    public String name() {
        return "manual_orders";
    }

    @Override
    public SendMessage render(Long chatId) {
        log.info("Рендер раздела «Ордера» для chatId={}", chatId);
        return SendMessage.builder()
                .chatId(chatId.toString())
                .text("*📝 Ордера*\nЗдесь будет список и управление вашими ордерами.")
                .parseMode("Markdown")
                .replyMarkup(keyboard)
                .build();
    }

    @Override
    public String handleInput(Update update) {
        if (update.hasCallbackQuery()) {
            String d = update.getCallbackQuery().getData();
            if ("manual_trading_settings".equals(d)) {
                return "manual_trading_settings";
            }
        }
        return name();
    }
}
