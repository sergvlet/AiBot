package com.chicu.aibot.bot.menu.state;

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
public class AiTradingState implements MenuState {

    private final InlineKeyboardMarkup keyboard;

    public AiTradingState() {
        this.keyboard = InlineKeyboardMarkup.builder()
                .keyboard(List.of(

                        List.of(InlineKeyboardButton.builder()
                                .text("📂 Выбор стратегии")
                                .callbackData("ai_select_strategy")
                                .build()),

                        List.of(InlineKeyboardButton.builder()
                                .text("⚙️ Настройки параметров")
                                .callbackData("ai_settings_params")
                                .build()),

                        List.of(
                                InlineKeyboardButton.builder()
                                        .text("📈 Статистика")
                                        .callbackData("ai_stats")
                                        .build(),
                                InlineKeyboardButton.builder()
                                        .text("🔔 Уведомления")
                                        .callbackData("ai_notifications")
                                        .build()
                        ),

                        // ✅ Универсальная кнопка управления балансом
                        List.of(InlineKeyboardButton.builder()
                                .text("💰 Управление балансом")
                                .callbackData("balance_menu")
                                .build()),

                        List.of(InlineKeyboardButton.builder()
                                .text("⬅️ Назад")
                                .callbackData("main")
                                .build())
                ))
                .build();
    }

    @Override
    public String name() {
        return "ai_trading";
    }

    @Override
    public SendMessage render(Long chatId) {
        log.info("Рендер AI-меню для chatId={}", chatId);
        return SendMessage.builder()
                .chatId(chatId.toString())
                .text("*🤖 AI-торговля*\nВыберите действие:")
                .parseMode("Markdown")
                .replyMarkup(keyboard)
                .build();
    }

    @Override
    public String handleInput(Update update) {
        if (!update.hasCallbackQuery()) {
            return name();
        }
        String data = update.getCallbackQuery().getData();
        log.info("AI-меню: нажата кнопка '{}'", data);
        return switch (data) {
            case "ai_select_strategy" -> "ai_select_strategy";
            case "ai_settings_params" -> "ai_settings_params";
            case "ai_stats"           -> "ai_stats";
            case "ai_notifications"   -> "ai_notifications";
            case "balance_menu"       -> "balance_menu"; // ✅ универсальный переход
            case "main"               -> MenuService.MAIN_MENU;
            default -> {
                log.warn("Неизвестный callback '{}' в AI-меню, остаёмся", data);
                yield name();
            }
        };
    }
}
