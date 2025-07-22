package com.chicu.aibot.bot.menu.core;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Хранит для каждого chatId:
 * 1) messageId отправленного меню
 * 2) текущее состояние
 * 3) текущее редактируемое поле
 * 4) ключ следующего текстового значения (nextValueKey)
 */
@Component
public class MenuSessionService {

    private final Map<Long, Integer> menuMessages   = new ConcurrentHashMap<>();
    private final Map<Long, String>  currentStates  = new ConcurrentHashMap<>();
    private final Map<Long, String>  editingFields  = new ConcurrentHashMap<>();
    // ВСЁ, что нужно для «ожидания» текстового ввода
    private final Map<Long, String>  nextValueKeys  = new ConcurrentHashMap<>();

    // --- menuMessageId ---
    public Integer getMenuMessageId(Long chatId) {
        return menuMessages.get(chatId);
    }
    public void setMenuMessageId(Long chatId, Integer messageId) {
        menuMessages.put(chatId, messageId);
    }

    // --- state ---
    public String getCurrentState(Long chatId) {
        return currentStates.get(chatId);
    }
    public void setCurrentState(Long chatId, String state) {
        currentStates.put(chatId, state);
    }

    // --- editingField ---
    public String getEditingField(Long chatId) {
        return editingFields.get(chatId);
    }
    public void setEditingField(Long chatId, String fieldKey) {
        editingFields.put(chatId, fieldKey);
    }
    public void clearEditingField(Long chatId) {
        editingFields.remove(chatId);
    }

    // --- nextValue ---
    /**
     * Устанавливает, какое конкретно текстовое значение бот сейчас ждёт.
     * Например: "EXCHANGE_PUBLIC_KEY" или "EXCHANGE_SECRET_KEY".
     */
    public void setNextValue(Long chatId, String nextValueKey) {
        nextValueKeys.put(chatId, nextValueKey);
    }

    /**
     * Возвращает ключ ожидаемого текстового значения, или null, если ввода нет.
     */
    public String getNextValue(Long chatId) {
        return nextValueKeys.get(chatId);
    }

    /**
     * Сбрасывает ожидание текстового ввода.
     */
    public void clearNextValue(Long chatId) {
        nextValueKeys.remove(chatId);
    }

}
