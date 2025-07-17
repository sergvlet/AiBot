package com.chicu.aibot.bot.menu.core;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Хранит для каждого chatId:
 * 1) messageId отправленного меню, чтобы при callback’е редактировать одно и то же сообщение
 * 2) ключ текущего состояния (state), чтобы MenuService мог переключаться между состояниями
 * 3) ключ текущего редактируемого поля (editingField) для «универсального» редактора параметров
 */
@Component
public class MenuSessionService {

    // messageId текущего меню для данного chatId
    private final Map<Long, Integer> menuMessages = new ConcurrentHashMap<>();

    // текущее состояние (state) для данного chatId
    private final Map<Long, String> currentStates = new ConcurrentHashMap<>();

    // текущее редактируемое поле (например, "gridSizePct") для данного chatId
    private final Map<Long, String> editingFields = new ConcurrentHashMap<>();

    // --- messageId API ---
    public Integer getMenuMessageId(Long chatId) {
        return menuMessages.get(chatId);
    }

    public void setMenuMessageId(Long chatId, Integer messageId) {
        menuMessages.put(chatId, messageId);
    }

    // --- state API ---
    /**
     * Возвращает последний сохранённый state для chatId или null, если его нет.
     */
    public String getCurrentState(Long chatId) {
        return currentStates.get(chatId);
    }

    /**
     * Сохраняет новое state для chatId.
     */
    public void setCurrentState(Long chatId, String state) {
        currentStates.put(chatId, state);
    }

    // --- editingField API ---
    /**
     * Сохраняет, какое именно поле сейчас редактируется для данного chatId.
     */
    public void setEditingField(Long chatId, String fieldKey) {
        editingFields.put(chatId, fieldKey);
    }

    /**
     * Возвращает ключ поля, которое сейчас редактируется, или null, если никакое.
     */
    public String getEditingField(Long chatId) {
        return editingFields.get(chatId);
    }

    /**
     * Сбрасывает текущее редактируемое поле.
     */
    public void clearEditingField(Long chatId) {
        editingFields.remove(chatId);
    }
}
