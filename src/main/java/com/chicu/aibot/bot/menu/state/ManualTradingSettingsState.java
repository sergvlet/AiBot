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
public class ManualTradingSettingsState implements MenuState {

    private final InlineKeyboardMarkup keyboard;

    public ManualTradingSettingsState() {
        this.keyboard = InlineKeyboardMarkup.builder()
            .keyboard(List.of(
                List.of(
                    InlineKeyboardButton.builder()
                        .text("🔄 Spot")
                        .callbackData("manual_spot")
                        .build(),
                    InlineKeyboardButton.builder()
                        .text("📈 Futures")
                        .callbackData("manual_futures")
                        .build(),
                    InlineKeyboardButton.builder()
                        .text("⚖️ Margin")
                        .callbackData("manual_margin")
                        .build()
                ),
                List.of(
                    InlineKeyboardButton.builder()
                        .text("📝 Ордера")
                        .callbackData("manual_orders")
                        .build(),
                    InlineKeyboardButton.builder()
                        .text("💰 Баланс")
                        .callbackData("manual_balance")
                        .build(),
                    InlineKeyboardButton.builder()
                        .text("🕒 История")
                        .callbackData("manual_history")
                        .build()
                ),
                List.of(
                    InlineKeyboardButton.builder()
                        .text("⚙️ Настройки")
                        .callbackData("manual_settings")
                        .build(),
                    InlineKeyboardButton.builder()
                        .text("⬅️ Назад")
                        .callbackData(MenuService.MAIN_MENU)
                        .build()
                )
            ))
            .build();
    }

    @Override
    public String name() {
        return "manual_trading_settings";
    }

    @Override
    public SendMessage render(Long chatId) {
        log.info("Рендер подменю ручной торговли для chatId={}", chatId);
        return SendMessage.builder()
                .chatId(chatId.toString())
                .text("*✋ Ручная торговля*\nВыберите раздел:")
                .parseMode("Markdown")
                .replyMarkup(keyboard)
                .build();
    }

    @Override
    public String handleInput(Update update) {
        if (update.hasCallbackQuery()) {
            String data = update.getCallbackQuery().getData();
            log.info("ManualTradingSettingsState: нажата кнопка '{}'", data);
            return switch (data) {
                case "manual_spot"      -> "manual_spot";
                case "manual_futures"   -> "manual_futures";
                case "manual_margin"    -> "manual_margin";
                case "manual_orders"    -> "manual_orders";
                case "manual_balance"   -> "manual_balance";
                case "manual_history"   -> "manual_history";
                case "manual_settings"  -> "manual_settings";
                case MenuService.MAIN_MENU -> MenuService.MAIN_MENU;
                default -> {
                    log.warn("Неизвестный callback '{}' в ручной торговле, остаёмся", data);
                    yield name();
                }
            };
        }
        log.info("ManualTradingSettingsState: возвращаем меню");
        return name();
    }
}
