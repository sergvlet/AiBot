package com.chicu.aibot.bot.menu.feature.ai.strategy;

import com.chicu.aibot.bot.menu.core.MenuState;
import com.chicu.aibot.trading.core.SchedulerService;
import com.chicu.aibot.strategy.StrategyType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AiSelectStrategyState implements MenuState {
    public static final String NAME = "ai_select_strategy";

    private final SchedulerService schedulerService;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public SendMessage render(Long chatId) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (StrategyType type : StrategyType.values()) {
            boolean active = schedulerService.isStrategyActive(chatId, type.name());
            String statusText = active ? "🟢 Запущена" : "🔴 Остановлена";
            String buttonText = String.format("%s — %s", type.getLabel(), statusText);

            // callbackData должно совпадать с именем состояния конфигурации
            String callbackData = switch (type) {
                case SCALPING -> "ai_trading_scalping_config";
                case FIBONACCI_GRID -> "ai_trading_fibonacci_config";
                case RSI_EMA -> "ai_trading_rsi_ema_config";
                case MA_CROSSOVER -> "ai_trading_ma_crossover_config";
                case BOLLINGER_BANDS -> "ai_trading_bollinger_config";
            };

            rows.add(List.of(
                    InlineKeyboardButton.builder()
                            .text(buttonText)
                            .callbackData(callbackData)
                            .build()
            ));
        }

        // Кнопка «Назад» в главное меню AI Trading
        rows.add(List.of(
                InlineKeyboardButton.builder()
                        .text("‹ Назад")
                        .callbackData("ai_trading")
                        .build()
        ));

        return SendMessage.builder()
                .chatId(chatId.toString())
                .parseMode("Markdown")
                .text("""
                    *Выбор стратегий AI*
                                            
                    Нажмите на стратегию, чтобы перейти к её настройке и запуску/остановке.""")
                .replyMarkup(new InlineKeyboardMarkup(rows))
                .build();
    }

    @Override
    public String handleInput(Update update) {
        if (!update.hasCallbackQuery()) {
            return NAME;
        }
        String data = update.getCallbackQuery().getData();
        // Переход в конфигурацию или возврат
        if ("ai_trading".equals(data)) {
            return "ai_trading";
        }
        return data; // т.к. callbackData мы задали как имя нужного состояния-конфига
    }
}
