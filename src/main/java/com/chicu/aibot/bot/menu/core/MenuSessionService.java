package com.chicu.aibot.bot.menu.core;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MenuSessionService {

    private final Map<Long, Integer> menuMessages   = new ConcurrentHashMap<>();
    private final Map<Long, String>  currentStates  = new ConcurrentHashMap<>();
    private final Map<Long, String>  editingFields  = new ConcurrentHashMap<>();
    private final Map<Long, String>  nextValueKeys  = new ConcurrentHashMap<>();
    private final Map<Long, String>  returnStates   = new ConcurrentHashMap<>();

    /** Новое хранилище: для каждого chatId — своя карта ключ→значение */
    private final Map<Long, Map<String, Object>> attributes = new ConcurrentHashMap<>();

    // menuMessageId
    public Integer getMenuMessageId(Long chatId) { return menuMessages.get(chatId); }
    public void setMenuMessageId(Long chatId, Integer messageId) { menuMessages.put(chatId, messageId); }

    // state
    public String getCurrentState(Long chatId) { return currentStates.get(chatId); }
    public void setCurrentState(Long chatId, String state) { currentStates.put(chatId, state); }

    // editingField
    public void setEditingField(Long chatId, String key) { editingFields.put(chatId, key); }
    public String getEditingField(Long chatId)   { return editingFields.get(chatId); }
    public void clearEditingField(Long chatId)   { editingFields.remove(chatId); }

    // nextValue
    public void setNextValue(Long chatId, String key)   { nextValueKeys.put(chatId, key); }
    public String getNextValue(Long chatId)             { return nextValueKeys.get(chatId); }
    public void clearNextValue(Long chatId)             { nextValueKeys.remove(chatId); }

    // returnState
    public void setReturnState(Long chatId, String state) { returnStates.put(chatId, state); }
    public String getReturnState(Long chatId)             { return returnStates.get(chatId); }

    /**
     * Сохранить любое значение в сессии пользователя.
     * Если для этого chatId ещё нет карты атрибутов — создаём.
     */
    public void setAttribute(Long chatId, String key, Object value) {
        attributes
                .computeIfAbsent(chatId, id -> new ConcurrentHashMap<>())
                .put(key, value);
    }

    /**
     * Получить ранее сохранённое значение (или null).
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(Long chatId, String key) {
        Map<String, Object> map = attributes.get(chatId);
        if (map == null) return null;
        return (T) map.get(key);
    }

    /**
     * Удалить значение из сессии.
     */
    public void removeAttribute(Long chatId, String key) {
        Map<String, Object> map = attributes.get(chatId);
        if (map != null) {
            map.remove(key);
        }
    }
    
}
