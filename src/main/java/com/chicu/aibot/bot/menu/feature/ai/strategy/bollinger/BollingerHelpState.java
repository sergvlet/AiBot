package com.chicu.aibot.bot.menu.feature.ai.strategy.bollinger;

import com.chicu.aibot.bot.menu.core.MenuState;
import com.chicu.aibot.bot.menu.feature.ai.strategy.AiSelectStrategyState;
import com.chicu.aibot.bot.ui.AdaptiveKeyboard;
import com.chicu.aibot.bot.ui.UiAutorefreshService;
import com.chicu.aibot.strategy.bollinger.model.BollingerStrategySettings;
import com.chicu.aibot.strategy.bollinger.service.BollingerStrategySettingsService;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

@Component
@RequiredArgsConstructor
public class BollingerHelpState implements MenuState {

    public static final String NAME = "ai_trading_bollinger_help";

    // навигация
    private static final String BTN_BACK_PANEL   = BollingerConfigState.NAME;
    private static final String BTN_SELECT_STRAT = AiSelectStrategyState.NAME;
    private static final String BTN_AI_TRADING   = "ai_trading";

    // пресеты
    private static final String PRESET_START     = "boll_preset_start";
    private static final String PRESET_VOLATILE  = "boll_preset_vol";
    private static final String PRESET_TREND     = "boll_preset_trend";
    private static final String PRESET_RESET     = "boll_preset_reset";

    private final BollingerStrategySettingsService settingsService;
    private final UiAutorefreshService uiAutorefresh;

    @Override public String name() { return NAME; }

    @Override
    public SendMessage render(Long chatId) {
        String text = ("""
                *📘 Bollinger Bands — полная справка*

                *Индикатор.* Полосы Боллинджера = `SMA(period)` и два канала на расстоянии `k·σ` от SMA.
                • В *флэте* цена часто возвращается к средней.
                • В *тренде* выход за внешнюю полосу может означать продолжение движения.

                *Логика входов/выходов:*
                • *LONG:* касание/прокол нижней полосы → покупка.
                • *SHORT:* касание/прокол верхней полосы → продажа.
                • Выход: по `TP%`/`SL%` (жёстко) или возврат к SMA (если так реализовано в вашей стратегии).

                *Кнопки панели и что они делают:*
                • 🎯 *Символ* — выбор пары (например, `ETHUSDT`).
                • ⏱ *ТФ* — таймфрейм свечей/сигналов (быстрый выбор от `1s` до `1M`).
                • 🧮 *История* — сколько последних свечей хранить/анализировать.
                • 💰 *Объём* — размер одной сделки (в вашей логике).
                • 📏 *SMA* — период простой скользящей (например, `20`).
                • σ *Коэф.* — множитель стандартного отклонения (`k`, например, `2.0`).
                • 📈/*📉* *LONG/SHORT* — включение направлений.
                • 🎯 *TP%* / 🛡 *SL%* — цели профита / ограничения убытка.
                • ▶️ *Вкл/Выкл* — запустить/остановить бота.
                • ⟳ *Обновить* — ручная перерисовка панели.

                *Рекомендации:*
                • *Флэт:* `SMA 20–50`, `k 2.0–2.5`, `TP 0.5–1.0%`, `SL 0.4–0.8%`.
                • *Тренд:* смещайтесь к `SMA 50–100` или `k 2.5–3.0`; `TP` выше, `SL` осторожнее.
                • Чем ниже ТФ — тем больше шум и влияние комиссий/спреда.

                Ниже — *пресеты* для быстрого старта. Нажмите — параметры применятся и вы вернётесь на панель.

                *Пресеты:*
                • *Старт* — аккуратный базовый профиль:
                  ТФ `1m`, История `720`, SMA `20`, σ `2.0`, TP `0.5%`, SL `0.4%`.
                • *Волатильный* — для «рваного» рынка:
                  ТФ `30s`, История `720`, SMA `20`, σ `2.5`, TP `0.6%`, SL `0.8%`.
                • *Тренд* — для устойчивого тренда:
                  ТФ `5m`, История `720`, SMA `50`, σ `2.0`, TP `1.2%`, SL `0.6%`.
                • *Сброс* — вернуть дефолт:
                  ТФ `1m`, История `520`, SMA `20`, σ `2.0`, TP `1.0%`, SL `0.5%`.

                _Пресеты не меняют символ, объём, направления (LONG/SHORT) и не стартуют/останавливают стратегию._
                """).stripTrailing();

        // --- кнопки (адаптивная раскладка) ---
        List<InlineKeyboardButton> presets1 = List.of(
                AdaptiveKeyboard.btn("⭐ Старт", PRESET_START),
                AdaptiveKeyboard.btn("🌪 Волатильный", PRESET_VOLATILE)
        );
        List<InlineKeyboardButton> presets2 = List.of(
                AdaptiveKeyboard.btn("📈 Тренд", PRESET_TREND),
                AdaptiveKeyboard.btn("♻️ Сброс", PRESET_RESET)
        );
        List<InlineKeyboardButton> nav = List.of(
                AdaptiveKeyboard.btn("‹ Назад к панели", BTN_BACK_PANEL),
                AdaptiveKeyboard.btn("Выбор стратегии", BTN_SELECT_STRAT),
                AdaptiveKeyboard.btn("AI-меню", BTN_AI_TRADING)
        );

        InlineKeyboardMarkup kb = AdaptiveKeyboard.markupFromGroups(List.of(presets1, presets2, nav));

        return SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode("Markdown")
                .disableWebPagePreview(true)
                .replyMarkup(kb)
                .build();
    }

    @Override
    public String handleInput(Update update) {
        if (update == null || !update.hasCallbackQuery()) return NAME;

        final String data   = update.getCallbackQuery().getData();
        final Long   chatId = update.getCallbackQuery().getMessage().getChatId();

        // пресеты — применяем и возвращаемся на панель
        switch (data) {
            case PRESET_START   -> { applyPreset(chatId, Preset.START);   return BollingerConfigState.NAME; }
            case PRESET_VOLATILE-> { applyPreset(chatId, Preset.VOL);     return BollingerConfigState.NAME; }
            case PRESET_TREND   -> { applyPreset(chatId, Preset.TREND);   return BollingerConfigState.NAME; }
            case PRESET_RESET   -> { applyPreset(chatId, Preset.RESET);   return BollingerConfigState.NAME; }
        }

        // навигация
        return switch (data) {
            case BTN_BACK_PANEL   -> BollingerConfigState.NAME;
            case BTN_SELECT_STRAT -> AiSelectStrategyState.NAME;
            case BTN_AI_TRADING   -> "ai_trading";
            default               -> NAME;
        };
    }

    /* ================= пресеты ================= */

    private enum Preset { START, VOL, TREND, RESET }

    private void applyPreset(Long chatId, Preset p) {
        // временно отключим автообновление панели, чтобы снизить шанс гонок
        uiAutorefresh.disable(chatId, BollingerConfigState.NAME);

        int attempts = 0;
        while (true) {
            attempts++;
            try {
                BollingerStrategySettings s = settingsService.getOrCreate(chatId);
                mutatePreset(s, p);
                settingsService.save(s);
                break; // успех
            } catch (OptimisticLockingFailureException | OptimisticLockException e) {
                if (attempts >= 3) throw e;
                try { Thread.sleep(30L); } catch (InterruptedException ignored) {}
            }
        }

        // включим автообновление: панель подхватит новые параметры
        uiAutorefresh.enable(chatId, BollingerConfigState.NAME);
    }

    private static void mutatePreset(BollingerStrategySettings s, Preset p) {
        switch (p) {
            case START -> {
                s.setTimeframe("1m");
                s.setCachedCandlesLimit(720);
                s.setPeriod(20);
                s.setStdDevMultiplier(2.0);
                s.setTakeProfitPct(0.5);
                s.setStopLossPct(0.4);
            }
            case VOL -> {
                s.setTimeframe("30s");
                s.setCachedCandlesLimit(720);
                s.setPeriod(20);
                s.setStdDevMultiplier(2.5);
                s.setTakeProfitPct(0.6);
                s.setStopLossPct(0.8);
            }
            case TREND -> {
                s.setTimeframe("5m");
                s.setCachedCandlesLimit(720);
                s.setPeriod(50);
                s.setStdDevMultiplier(2.0);
                s.setTakeProfitPct(1.2);
                s.setStopLossPct(0.6);
            }
            case RESET -> {
                s.setTimeframe("1m");
                s.setCachedCandlesLimit(520);
                s.setPeriod(20);
                s.setStdDevMultiplier(2.0);
                s.setTakeProfitPct(1.0);
                s.setStopLossPct(0.5);
            }
        }
    }
}
