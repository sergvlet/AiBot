package com.chicu.aibot.bot.menu.feature.ai.strategy;

import com.chicu.aibot.bot.menu.core.MenuState;
import com.chicu.aibot.strategy.StrategyType;
import com.chicu.aibot.strategy.model.AiTradingSettings;
import com.chicu.aibot.strategy.service.AiTradingSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AiSelectStrategyState implements MenuState {
    public static final String NAME = "ai_select_strategy";

    private final AiTradingSettingsService settingsService;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public SendMessage render(Long chatId) {
        AiTradingSettings cfg = settingsService.getOrCreate(chatId);
        Set<StrategyType> selected = cfg.getSelectedStrategies();

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (StrategyType type : StrategyType.values()) {
            boolean sel = selected.contains(type);
            InlineKeyboardButton btn = InlineKeyboardButton.builder()
                .text((sel ? "✅ " : "") + type.getLabel())
                .callbackData("strategy_toggle:" + type.name())
                .build();
            rows.add(List.of(btn));
        }
        // Кнопка «Назад» в AI-меню
        InlineKeyboardButton back = InlineKeyboardButton.builder()
            .text("‹ Назад")
            .callbackData("ai_trading")
            .build();
        rows.add(List.of(back));

        return SendMessage.builder()
            .chatId(chatId.toString())
            .text("""
                    *Выбор стратегий AI*
                    
                    ✅ — включено. Нажмите, чтобы переключить или сразу перейти к настройке:""")
            .parseMode("Markdown")
            .replyMarkup(new InlineKeyboardMarkup(rows))
            .build();
    }

    @Override
    public String handleInput(Update update) {
        if (!update.hasCallbackQuery()) {
            return NAME;
        }
        var cq     = update.getCallbackQuery();
        String data = cq.getData();
        Long   chatId = cq.getMessage().getChatId();

        if (data.startsWith("strategy_toggle:")) {
            String code = data.substring("strategy_toggle:".length());
            StrategyType type = StrategyType.findByCode(code);

            // переключаем включение/выключение
            AiTradingSettings cfg = settingsService.getOrCreate(chatId);
            boolean was = cfg.getSelectedStrategies().contains(type);
            settingsService.updateSelectedStrategies(chatId, type, !was);

            // если теперь включено — сразу показываем экран настройки
            if (!was) {
                return switch(type) {
                    case SCALPING       -> "scalping_config";
                    case FIBONACCI_GRID -> "ai_trading_fibonacci_config";
                    case RSI_EMA        -> "ai_trading_rsi_ema_config";
                    case MA_CROSSOVER   -> "ai_trading_ma_crossover_config";
                    case BOLLINGER_BANDS-> "ai_trading_bollinger_config";
                };
            }
            // если выключили — остаёмся на экране списка
            return NAME;
        }

        if ("ai_trading".equals(data)) {
            return "ai_trading";
        }
        return NAME;
    }
}
