package com.chicu.aibot.bot.menu.feature.register;

import com.chicu.aibot.bot.menu.core.MenuService;
import com.chicu.aibot.bot.menu.core.MenuState;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

@Component
public class RegisterState implements MenuState {

    @Override
    public String name() {
        return "register";
    }

    @Override
    public SendMessage render(Long chatId) {
        // строим клавиатуру с кнопкой «Назад»
        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboard(List.of(
                        List.of(
                                InlineKeyboardButton.builder()
                                        .text("⬅️ Назад")
                                        .callbackData(MenuService.MAIN_MENU)
                                        .build()
                        )
                ))
                .build();

        return SendMessage.builder()
                .chatId(chatId.toString())
                .text("Функция регистрации пока в разработке 🛠️\n\nНажмите «Назад», чтобы вернуться в главное меню.")
                .replyMarkup(keyboard)
                .build();
    }

    @Override
    public String handleInput(Update update) {
        if (update.hasCallbackQuery()) {
            String data = update.getCallbackQuery().getData();
            if (MenuService.MAIN_MENU.equals(data)) {
                return MenuService.MAIN_MENU;
            }
        }
        // во всех остальных случаях тоже возвращаем в главное меню
        return MenuService.MAIN_MENU;
    }
}
