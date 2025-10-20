package com.chicu.aibot.bot.menu.feature.ai.strategy.ml_invest;

import com.chicu.aibot.bot.menu.core.MenuState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

@Component(AiTradingMlInvestHelpState.NAME)
@RequiredArgsConstructor
public class AiTradingMlInvestHelpState implements MenuState {

    public static final String NAME = "ai_trading_ml_invest_help";

    @Override public String name() { return NAME; }

    @Override
    public SendMessage render(Long chatId) {
        String text = """
                🤖 *ML Invest — помощь*

                • Укажите путь к модели (`modelPath`, .joblib/.onnx)
                • Настройте `timeframe`, `cachedCandlesLimit`
                • `buyThreshold` / `sellThreshold` (0.05…0.99)
                • `takeProfitPct` / `stopLossPct`
                • Выберите режим объёма: Qty (фикс. количество) или Quote (сумма)
                """;

        InlineKeyboardButton back = new InlineKeyboardButton("‹ Назад");
        back.setCallbackData(AiTradingMlInvestConfigState.NAME);
        InlineKeyboardMarkup kb = new InlineKeyboardMarkup(List.of(List.of(back)));

        return SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(text)
                .parseMode("Markdown")
                .replyMarkup(kb)
                .build();
    }

    public SendMessage render(Update update) { return render(extractChatId(update)); }

    @Override
    public String handleInput(Update update) {
        if (update != null && update.hasCallbackQuery()) {
            String data = update.getCallbackQuery().getData();
            if (AiTradingMlInvestConfigState.NAME.equals(data)) return AiTradingMlInvestConfigState.NAME;
        }
        return NAME;
    }

    private Long extractChatId(Update u) {
        if (u == null) return 0L;
        if (u.hasCallbackQuery() && u.getCallbackQuery().getMessage() != null) {
            return u.getCallbackQuery().getMessage().getChatId();
        }
        if (u.hasMessage() && u.getMessage().getChatId() != null) {
            return u.getMessage().getChatId();
        }
        return 0L;
    }
}
