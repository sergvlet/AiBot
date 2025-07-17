package com.chicu.aibot.bot.menu.core;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Интерфейс экрана-меню.
 */
public interface MenuState {
    /** Уникальное имя состояния. */
    String name();

    /** Генерирует SendMessage для отображения этого экрана. */
    SendMessage render(Long chatId);

    /**
     * Обрабатывает входящее Update (сообщение или callback) и возвращает:
     * - имя следующего состояния,
     * — MenuService.BACK для возврата назад,
     * — или своё name(), чтобы остаться.
     */
    String handleInput(Update update);
}
