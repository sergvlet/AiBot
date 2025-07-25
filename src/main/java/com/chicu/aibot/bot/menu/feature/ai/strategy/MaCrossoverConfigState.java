package com.chicu.aibot.bot.menu.feature.ai.strategy;

import com.chicu.aibot.bot.menu.core.MenuState;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

@Component
public class MaCrossoverConfigState implements MenuState {
    public static final String NAME = "ai_trading_ma_crossover_config";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public SendMessage render(Long chatId) {
        String text = """
                *MA Crossover Strategy*
                
                Использует пересечение двух скользящих средних (быстрой и медленной) для сигналов.
                
                *Дефолтные настройки:*
                - Быстрая MA период: 20
                - Медленная MA период: 50
                """;

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
