// src/main/java/com/chicu/aibot/bot/menu/feature/ai/strategy/RsiEmaConfigState.java
package com.chicu.aibot.bot.menu.feature.ai.strategy;

import com.chicu.aibot.bot.menu.core.MenuState;
import lombok.NonNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

@Component
public class RsiEmaConfigState implements MenuState {
    public static final String NAME = "ai_trading_rsi_ema_config";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public SendMessage render(Long chatId) {
        String text = "*RSI + EMA Strategy*\n\n"
            + "Комбинирует RSI для поиска перекупленности/перепроданности и EMA для подтверждения тренда.\n\n"
            + "*Дефолтные настройки:*\n"
            + "- Период RSI: 14\n"
            + "- Верхний порог RSI: 70\n"
            + "- Нижний порог RSI: 30\n"
            + "- Период EMA: 50\n";

        InlineKeyboardButton back = InlineKeyboardButton.builder()
            .text("‹ Назад")
            .callbackData("ai_select_strategy")
            .build();

        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
            .keyboard(List.of(List.of(back)))
            .build();

        return SendMessage.builder()
            .chatId(chatId.toString())
            .text(text)
            .parseMode("Markdown")
            .replyMarkup(markup)
            .build();
    }

    @Override
    public @NonNull String handleInput(Update update) {
        if (update.hasCallbackQuery()
            && "ai_select_strategy".equals(update.getCallbackQuery().getData())) {
            return AiSelectStrategyState.NAME;
        }
        return NAME;
    }
}
