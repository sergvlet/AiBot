package com.chicu.aibot.bot.menu.feature.ai.strategy.scalping;

import com.chicu.aibot.bot.menu.core.MenuSessionService;
import com.chicu.aibot.bot.menu.core.MenuState;
import com.chicu.aibot.bot.menu.feature.common.AiSelectSymbolState;
import com.chicu.aibot.strategy.scalping.model.ScalpingStrategySettings;
import com.chicu.aibot.strategy.scalping.service.ScalpingStrategySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ScalpingHelpState implements MenuState {

    public static final String NAME = "ai_trading_scalping_help";

    private static final String BTN_BACK_CONFIG   = ScalpingConfigState.NAME;
    private static final String BTN_TO_SYMBOL     = "scalp_help_goto_symbol";
    private static final String BTN_PRESET_CONS   = "scalp_help_preset_conservative";
    private static final String BTN_PRESET_BAL    = "scalp_help_preset_balanced";
    private static final String BTN_PRESET_AGG    = "scalp_help_preset_aggressive";
    private static final String BTN_RESET_DEFAULT = "scalp_help_reset_defaults";

    private final ScalpingStrategySettingsService settingsService;
    private final MenuSessionService sessionService;

    @Override public String name() { return NAME; }

    @Override
    public SendMessage render(Long chatId) {
        String text = """
                *ℹ️ Scalping — справка и быстрые пресеты*

                Алгоритм отслеживает короткие импульсы цены и входит при превышении *ΔЦены (%)*, контролируя *макс. спред (%)*.\s
                Выход — по *TP (%)* или *SL (%)*. Чувствительность влияет *Окно* (ширина анализа в свечах) и *История* (глубина кэша свечей).
               \s
                *Параметры:*
                • *🎯 Символ* — торговая пара (ETHUSDT и т.п.)
                • *💰 Объём сделки* — размер рыночного ордера
                • *⏱ Таймфрейм* — интервал свечи/пересчёта (от 1s и выше)
                • *История* — сколько свечей держать в кэше
                • *🪟 Окно* — размер скользящего окна анализа
                • *ΔЦены (%)* — минимальный импульс для входа
                • *Макс. спред (%)* — фильтр ликвидности
                • *TP/SL (%)* — цели выхода
                               \s
                *Быстрые пресеты:*
                • *Conservative* — меньше входов, мягкие фильтры (для спокойного рынка)
                • *Balanced* — сбалансированный по частоте/риску (рекомендуется как старт)
                • *Aggressive* — больше входов, выше риск (для активного рынка)
               \s""";

        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(
                        btn("🧩 Conservative", BTN_PRESET_CONS),
                        btn("⚖️ Balanced",    BTN_PRESET_BAL),
                        btn("🔥 Aggressive",  BTN_PRESET_AGG)
                ))
                .keyboardRow(List.of(
                        btn("↩️ Сброс к умолчанию", BTN_RESET_DEFAULT)
                ))
                .keyboardRow(List.of(
                        btn("🎯 Выбрать символ…", BTN_TO_SYMBOL)
                ))
                .keyboardRow(List.of(
                        btn("‹ Назад к настройкам", BTN_BACK_CONFIG)
                ))
                .build();

        return SendMessage.builder()
                .chatId(chatId.toString())
                .parseMode("Markdown")
                .disableWebPagePreview(true)
                .text(text)
                .replyMarkup(markup)
                .build();
    }

    @Override
    public String handleInput(Update update) {
        if (update == null || !update.hasCallbackQuery()) return NAME;

        String data  = update.getCallbackQuery().getData();
        Long chatId  = update.getCallbackQuery().getMessage().getChatId();

        if (BTN_BACK_CONFIG.equals(data)) {
            return ScalpingConfigState.NAME;
        }
        if (BTN_TO_SYMBOL.equals(data)) {
            sessionService.setEditingField(chatId, "symbol");
            sessionService.setReturnState(chatId, ScalpingConfigState.NAME);
            return AiSelectSymbolState.NAME;
        }

        // Пресеты/сброс — сохраняем и СРАЗУ возвращаемся на панель, чтобы пользователь увидел изменения
        if (BTN_PRESET_CONS.equals(data)) {
            applyPreset(chatId, Preset.CONSERVATIVE);
            return ScalpingConfigState.NAME;
        }
        if (BTN_PRESET_BAL.equals(data)) {
            applyPreset(chatId, Preset.BALANCED);
            return ScalpingConfigState.NAME;
        }
        if (BTN_PRESET_AGG.equals(data)) {
            applyPreset(chatId, Preset.AGGRESSIVE);
            return ScalpingConfigState.NAME;
        }
        if (BTN_RESET_DEFAULT.equals(data)) {
            resetDefaults(chatId);
            return ScalpingConfigState.NAME;
        }

        return NAME;
    }

    // ===== helpers =====

    private InlineKeyboardButton btn(String text, String data) {
        return InlineKeyboardButton.builder().text(text).callbackData(data).build();
    }

    private enum Preset { CONSERVATIVE, BALANCED, AGGRESSIVE }

    private void applyPreset(Long chatId, Preset p) {
        ScalpingStrategySettings s = settingsService.getOrCreate(chatId);
        switch (p) {
            case CONSERVATIVE -> {
                s.setOrderVolume(0.50);   // меньший объём
                s.setTimeframe("3s");
                s.setCachedCandlesLimit(720);
                s.setWindowSize(150);
                s.setPriceChangeThreshold(0.50); // выше порог входа
                s.setSpreadThreshold(0.08);
                s.setTakeProfitPct(0.80);
                s.setStopLossPct(0.50);
            }
            case BALANCED -> {
                s.setOrderVolume(1.00);
                s.setTimeframe("1s");
                s.setCachedCandlesLimit(720);
                s.setWindowSize(120);
                s.setPriceChangeThreshold(0.40);
                s.setSpreadThreshold(0.10);
                s.setTakeProfitPct(1.00);
                s.setStopLossPct(0.50);
            }
            case AGGRESSIVE -> {
                s.setOrderVolume(1.20);
                s.setTimeframe("1s");
                s.setCachedCandlesLimit(520);
                s.setWindowSize(80);
                s.setPriceChangeThreshold(0.25); // чаще входы
                s.setSpreadThreshold(0.15);
                s.setTakeProfitPct(0.80);
                s.setStopLossPct(0.60);
            }
        }
        settingsService.save(s);
    }

    private void resetDefaults(Long chatId) {
        // те же значения, что обычно показывали по умолчанию на панели
        ScalpingStrategySettings s = settingsService.getOrCreate(chatId);
        s.setOrderVolume(1.00);
        s.setTimeframe("1s");
        s.setCachedCandlesLimit(520);
        s.setWindowSize(50);
        s.setPriceChangeThreshold(0.10);
        s.setSpreadThreshold(0.22);
        s.setTakeProfitPct(1.00);
        s.setStopLossPct(0.50);
        settingsService.save(s);
    }
}
