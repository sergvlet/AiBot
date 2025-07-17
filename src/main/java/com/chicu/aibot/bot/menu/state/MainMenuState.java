package com.chicu.aibot.bot.menu.state;

import com.chicu.aibot.bot.menu.core.MenuState;
import lombok.NonNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

@Component
public class MainMenuState implements MenuState {

    private final InlineKeyboardMarkup keyboard;

    public MainMenuState() {
        this.keyboard = InlineKeyboardMarkup.builder()
            .keyboard(List.of(
                // строка 1: AI-торговля, Выбор биржи, Ручная торговля
                List.of(
                    InlineKeyboardButton.builder()
                        .text("🤖 AI-торговля")
                        .callbackData("ai_trading")
                        .build(),
                    InlineKeyboardButton.builder()
                        .text("🏦 Выбор биржи")
                        .callbackData("exchange_select")
                        .build(),
                    InlineKeyboardButton.builder()
                        .text("✋ Ручная торговля")
                        .callbackData("manual_trading_settings")
                        .build()
                ),
                // строка 2: О боте, Регистрация
                List.of(
                    InlineKeyboardButton.builder()
                        .text("ℹ️ О боте")
                        .callbackData("about")
                        .build(),
                    InlineKeyboardButton.builder()
                        .text("📝 Регистрация")
                        .callbackData("register")
                        .build()
                ),
                // строка 3: Тарифы
                List.of(
                    InlineKeyboardButton.builder()
                        .text("💳 Тарифы")
                        .callbackData("plans")
                        .build()
                )
            ))
            .build();
    }

    @Override
    public String name() {
        return "main";
    }

    @Override
    public SendMessage render(Long chatId) {
        return SendMessage.builder()
            .chatId(chatId.toString())
            .text("*🏠 Главное меню*\nВыберите один из разделов ниже:")
            .parseMode("Markdown")
            .replyMarkup(keyboard)
            .build();
    }

    @Override
    public @NonNull String handleInput(Update update) {
        if (update.hasCallbackQuery()) {
            String data = update.getCallbackQuery().getData();
            return switch (data) {
                case "ai_trading"               -> "ai_trading";
                case "exchange_select"          -> "exchange_select";
                case "manual_trading_settings"  -> "manual_trading_settings";
                case "about"                    -> "about";
                case "register"                 -> "register";
                case "plans"                    -> "plans";
                default                         -> name();
            };
        }
        return name();
    }
}
