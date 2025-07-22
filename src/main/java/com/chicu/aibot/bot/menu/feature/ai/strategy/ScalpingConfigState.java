// src/main/java/com/chicu/aibot/bot/menu/feature/ai/strategy/ScalpingConfigState.java
package com.chicu.aibot.bot.menu.feature.ai.strategy;

import com.chicu.aibot.bot.menu.core.MenuState;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

@Component
public class ScalpingConfigState implements MenuState {
    public static final String NAME = "scalping_config";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public SendMessage render(Long chatId) {
        String text = "*Scalping Strategy*\n\n"
            + "Краткосрочная стратегия скальпинга, которая ищет мелкие ценовые колебания.\n\n"
            + "*Дефолтные настройки:*\n"
            + "- Размер окна (windowSize): 5\n"
            + "- Порог изменения цены (priceChangeThreshold): 0.2%\n"
            + "- Минимальный объём (minVolume): 10.0\n"
            + "- Порог спрэда (spreadThreshold): 0.2%\n"
            + "- Take profit: 1.0%\n"
            + "- Stop loss: 0.5%\n";

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
    public String handleInput(Update update) {
        if (update.hasCallbackQuery()
            && "ai_select_strategy".equals(update.getCallbackQuery().getData())) {
            return AiSelectStrategyState.NAME;
        }
        return NAME;
    }
}
