package com.chicu.aibot.bot.menu.feature.ai.strategy.scalping;

import com.chicu.aibot.bot.menu.core.MenuSessionService;
import com.chicu.aibot.bot.menu.core.MenuState;
import com.chicu.aibot.bot.menu.feature.ai.strategy.scalping.service.ScalpingPanelRenderer;
import com.chicu.aibot.bot.menu.feature.common.AiSelectSymbolState;
import com.chicu.aibot.bot.ui.UiAutorefreshService;
import com.chicu.aibot.strategy.scalping.model.ScalpingStrategySettings;
import com.chicu.aibot.strategy.scalping.service.ScalpingStrategySettingsService;
import com.chicu.aibot.trading.scheduler.SchedulerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class ScalpingConfigState implements MenuState {

    public static final String NAME = "ai_trading_scalping_config";

    // существующие callbacks
    private static final String BTN_EDIT_SYMBOL   = "edit_symbol";
    private static final String BTN_TOGGLE_ACTIVE = "scalp_toggle_active";
    private static final String BTN_REFRESH       = "scalp_refresh";
    private static final String BTN_HELP          = "scalp_help";
    private static final String BTN_HELP_BACK     = "scalp_help_back";

    private final ScalpingStrategySettingsService settingsService;
    private final MenuSessionService sessionService;
    private final SchedulerService schedulerService;
    private final ScalpingPanelRenderer panelRenderer;
    private final UiAutorefreshService uiAutorefresh;

    /** Чаты, где сейчас открыт режим "Справка". */
    private final Set<Long> helpMode = ConcurrentHashMap.newKeySet();

    @Override
    public String name() { return NAME; }

    @Override
    public SendMessage render(Long chatId) {
        // если пользователь в режиме "Справка" — рисуем её и НЕ включаем автorefresh
        if (helpMode.contains(chatId)) {
            return renderHelp(chatId);
        }

        // обычная панель + включаем автообновление (таймер сам сделает edit)
        uiAutorefresh.enable(chatId, NAME);
        return panelRenderer.render(chatId);
    }

    @Override
    public String handleInput(Update update) {
        if (!update.hasCallbackQuery()) return NAME;

        String data  = update.getCallbackQuery().getData();
        Long chatId  = update.getCallbackQuery().getMessage().getChatId();

        // ручное обновление панели
        if (BTN_REFRESH.equals(data)) {
            helpMode.remove(chatId);
            uiAutorefresh.enable(chatId, NAME);
            return NAME;
        }

        // запуск/остановка стратегии
        if (BTN_TOGGLE_ACTIVE.equals(data)) {
            ScalpingStrategySettings s = settingsService.getOrCreate(chatId);
            s.setActive(!s.isActive());
            if (s.isActive()) {
                schedulerService.startStrategy(chatId, s.getType().name());
            } else {
                schedulerService.stopStrategy(chatId, s.getType().name());
            }
            settingsService.save(s);
            return NAME;
        }

        // выход назад — отключаем автообновление
        if ("ai_trading".equals(data)) {
            helpMode.remove(chatId);
            uiAutorefresh.disable(chatId, NAME);
            return "ai_trading";
        }

        // выбор пары — отключаем автообновление и уходим на экран выбора
        if (BTN_EDIT_SYMBOL.equals(data)) {
            helpMode.remove(chatId);
            uiAutorefresh.disable(chatId, NAME);
            sessionService.setEditingField(chatId, "symbol");
            sessionService.setReturnState(chatId, NAME);
            return AiSelectSymbolState.NAME;
        }

        // редактирование числовых параметров — тоже отключаем автообновление
        if (data.startsWith("scalp_edit_")) {
            helpMode.remove(chatId);
            uiAutorefresh.disable(chatId, NAME);
            String field = data.substring("scalp_edit_".length());
            sessionService.setEditingField(chatId, field);
            return ScalpingAdjustState.NAME;
        }

        // ——— Справка ———
        if (BTN_HELP.equals(data)) {
            uiAutorefresh.disable(chatId, NAME);
            helpMode.add(chatId);
            return NAME;
        }

        if (BTN_HELP_BACK.equals(data)) {
            // выходим из help-режима: включаем автообновление и возвращаем обычную панель
            helpMode.remove(chatId);
            uiAutorefresh.enable(chatId, NAME);
            return NAME;
        }

        return NAME;
    }

    // ===================== HELP RENDER =====================

    private SendMessage renderHelp(Long chatId) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                InlineKeyboardButton.builder().text("‹ Назад к панели").callbackData(BTN_HELP_BACK).build()
        ));

        return SendMessage.builder()
                .chatId(chatId.toString())
                .text(helpText())
                .parseMode("Markdown")
                .disableWebPagePreview(true)
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                .build();
    }

    private String helpText() {
        return """
               *ℹ️ Скальпинг — описание стратегии*

               *Идея.*
               Стратегия ловит короткие импульсы цены. Вход — когда модуль изменения цены (Δ) за последнее окно
               превышает *Триггер входа, %*, при этом рыночный *спред* не шире *Макс. спред, %*.
               Выход — по *Тейк-профит, %* или *Стоп-лосс, %*.

               *Как это работает (вкратце):*
               1) По выбранному таймфрейму берётся `Окно` последних свечей.
               2) Считается процентное изменение цены в этом окне.
               3) Если |Δ| ≥ *Триггер входа, %* и спред ≤ *Макс. спред, %* — отправляется *рыночный ордер*:
                  • BUY — объём считается как процент от свободного QUOTE;
                  • SELL — как процент от свободного BASE.
               4) Позиция закрывается по *Тейк-профит, %* (фиксируем прибыль) или по *Стоп-лосс, %*.

               *Кнопки и настройки панели:*
               • 🎯 *Символ* — торговая пара (например, `ETHUSDT`).
               • 💰 *Объём сделки, %* — доля от свободного баланса (BUY — от QUOTE; SELL — от BASE).
               • 📋 *История* — лимит кэшируемых свечей (технический параметр).
               • 🪟 *Окно* — размер скользящего окна; меньше — чувствительнее и чаще сигналы.
               • ⚡ *Триггер входа, %* — минимальный импульс для входа; выше — реже, но чище.
               • ↔️ *Макс. спред, %* — фильтр ликвидности; при широком спреде вход блокируется.
               • 🎯 *Тейк-профит, %* — цель прибыли от цены входа.
               • 🛡 *Стоп-лосс, %* — ограничение убытка от цены входа.
               • ⏱ *Обновить* — ручная перерисовка панели.
               • ▶️ *Стратегия: ВКЛ/ВЫКЛ* — старт/стоп фонового цикла сигналов.

               *Советы:*
               • Начните с малого *объёма* и консервативных *TP/SL*.
               • Ложных входов много — увеличьте *Триггер входа* и/или *Окно*.
               • Входов мало — снизьте порог/окно, но следите за качеством сигналов.
               • На неликвидных парах поднимите *Макс. спред* или смените символ.

               _Тестируйте на тестнете/микро-объёмах: скальпинг чувствителен к комиссиям и проскальзыванию._
               """;
    }
}
