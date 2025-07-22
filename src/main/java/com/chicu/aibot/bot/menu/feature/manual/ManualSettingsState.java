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
public class ManualSettingsState implements MenuState {

    private final InlineKeyboardMarkup keyboard;

    public ManualSettingsState() {
        this.keyboard = InlineKeyboardMarkup.builder()
            .keyboard(List.of(
                // Управление ключами API
                List.of(
                    InlineKeyboardButton.builder()
                        .text("🔑 API Keys")
                        .callbackData("manual_settings_api_keys")
                        .build()
                ),
                // Параметры торговли
                List.of(
                    InlineKeyboardButton.builder()
                        .text("📊 Леверидж")
                        .callbackData("manual_settings_leverage")
                        .build(),
                    InlineKeyboardButton.builder()
                        .text("💸 Размер ордера")
                        .callbackData("manual_settings_order_size")
                        .build()
                ),
                // Уведомления
                List.of(
                    InlineKeyboardButton.builder()
                        .text("🔔 Уведомления")
                        .callbackData("manual_settings_notifications")
                        .build()
                ),
                // Назад
                List.of(
                    InlineKeyboardButton.builder()
                        .text("⬅️ Назад")
                        .callbackData("manual_trading_settings")
                        .build()
                )
            ))
            .build();
    }

    @Override
    public String name() {
        return "manual_settings";
    }

    @Override
    public SendMessage render(Long chatId) {
        log.info("Рендер подменю «Настройки» для chatId={}", chatId);
        return SendMessage.builder()
                .chatId(chatId.toString())
                .text("*⚙️ Настройки ручной торговли*\nВыберите пункт:")
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
        log.info("ManualSettingsState: нажат '{}'", data);
        return switch (data) {
            case "manual_settings_api_keys"        -> "manual_settings_api_keys";
            case "manual_settings_leverage"        -> "manual_settings_leverage";
            case "manual_settings_order_size"      -> "manual_settings_order_size";
            case "manual_settings_notifications"   -> "manual_settings_notifications";
            case "manual_trading_settings"         -> "manual_trading_settings";
            default -> {
                log.warn("Неизвестный callback '{}' в ManualSettings", data);
                yield name();
            }
        };
    }
}
