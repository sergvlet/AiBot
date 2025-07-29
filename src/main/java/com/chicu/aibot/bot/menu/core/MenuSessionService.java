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
    private final Map<Long, String> returnStates = new ConcurrentHashMap<>();

    // menuMessageId
    public Integer getMenuMessageId(Long chatId) { return menuMessages.get(chatId); }
    public void setMenuMessageId(Long chatId, Integer messageId) { menuMessages.put(chatId, messageId); }

    // state
    public String getCurrentState(Long chatId) { return currentStates.get(chatId); }
    public void setCurrentState(Long chatId, String state) { currentStates.put(chatId, state); }

    // editingField
    public void setEditingField(Long chatId, String key) { editingFields.put(chatId, key); }
    public String getEditingField(Long chatId) { return editingFields.get(chatId); }
    public void clearEditingField(Long chatId) { editingFields.remove(chatId); }

    // nextValue
    public void setNextValue(Long chatId, String key)   { nextValueKeys.put(chatId, key); }
    public String getNextValue(Long chatId)             { return nextValueKeys.get(chatId); }
    public void clearNextValue(Long chatId)             { nextValueKeys.remove(chatId); }

    public void setReturnState(Long chatId, String state) {
        returnStates.put(chatId, state);
    }

    public String getReturnState(Long chatId) {
        return returnStates.get(chatId);
    }
}

