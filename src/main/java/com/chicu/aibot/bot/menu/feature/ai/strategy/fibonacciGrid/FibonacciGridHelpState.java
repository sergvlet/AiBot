package com.chicu.aibot.bot.menu.feature.ai.strategy.fibonacciGrid;

import com.chicu.aibot.bot.menu.core.MenuState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

@Component
@RequiredArgsConstructor
public class FibonacciGridHelpState implements MenuState {

    public static final String NAME = "ai_trading_fibonacci_help";

    @Override
    public String name() { return NAME; }

    @Override
    public SendMessage render(Long chatId) {
        String text = """
                *ℹ️ Fibonacci Grid — описание*

                Эта стратегия строит «сетку» уровней вокруг текущей цены с шагом `Шаг сетки, %` и размещает лимитные заявки на покупку/продажу. По достижении уровня исполняется заявка и сразу выставляется тейк-профит `TP, %`. Опционально стоп-лосс `SL, %`.

                *Кнопки и параметры:*
                • *🎯 Символ* — торговая пара (например, ETHUSDT)
                • *💰 Объём, %* — объём ордера (в базовой валюте) \s
                • *🧱 Шаг сетки, %* — расстояние между соседними уровнями. \s
                • *📊 Макс. ордеров* — ограничение одновременно активных ордеров. \s
                • *📈 LONG / 📉 SHORT* — разрешение сторон торговли для построения сетки. \s
                • *🎯 TP, %* — цель прибыли от входа до фиксации. \s
                • *🛡 SL, %* — ограничение убытка (опционально). \s
                • *⏱ Таймфрейм* — интервал свечей/пересчёта. \s
                • *История* — объём данных для анализа. \s
                • *▶️ ВКЛ/ВЫКЛ* — запуск/остановка стратегии.

                *Как это работает (вкратце):*
                1) На основании последней цены строятся уровни вверх/вниз с заданным шагом. \s
                2) При разрешённой стороне (LONG/SHORT) ставятся лимитные заявки до лимита активных ордеров. \s
                3) Исполнившаяся заявка сопровождается выставлением TP (и, при необходимости, SL). \s
                4) По мере движения цены сетка обновляется.

                Совет: начните с меньшего *Шага сетки* и *Макс. ордеров*, включив только одну сторону (например LONG), оцените нагрузку на депозит и частоту сделок, затем увеличивайте параметры.
               \s""";

        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(
                        InlineKeyboardButton.builder().text("‹ Назад").callbackData(FibonacciGridConfigState.NAME).build()
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
        if (update != null && update.hasCallbackQuery()) {
            String data = update.getCallbackQuery().getData();
            if (FibonacciGridConfigState.NAME.equals(data)) {
                return FibonacciGridConfigState.NAME;
            }
        }
        return NAME;
    }
}
