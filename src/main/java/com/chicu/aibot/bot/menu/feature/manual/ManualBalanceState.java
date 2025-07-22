package com.chicu.aibot.bot.menu.feature.manual;

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
public class ManualBalanceState implements MenuState {

    private final InlineKeyboardMarkup keyboard;

    public ManualBalanceState() {
        this.keyboard = InlineKeyboardMarkup.builder()
            .keyboard(List.of(
                // Заглушка: здесь будет баланс
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
        return "manual_balance";
    }

    @Override
    public SendMessage render(Long chatId) {
        log.info("Рендер раздела «Баланс» для chatId={}", chatId);
        return SendMessage.builder()
                .chatId(chatId.toString())
                .text("*💰 Баланс*\nЗдесь вы увидите свои остатки на счетах.")
                .parseMode("Markdown")
                .replyMarkup(keyboard)
                .build();
    }

    @Override
    public String handleInput(Update update) {
        if (update.hasCallbackQuery() && 
            "manual_trading_settings".equals(update.getCallbackQuery().getData())) {
            return "manual_trading_settings";
        }
        return name();
    }
}
