// src/main/java/com/chicu/aibot/bot/menu/feature/common/SymbolSettingsService.java
package com.chicu.aibot.bot.menu.feature.common;

public interface SymbolSettingsService {
    /** Достанвливаем или создаём пустые настройки по chatId */
    Object getOrCreate(Long chatId);
    /** Сохраняем выбранный символ в этих настройках */
    void saveSymbol(Long chatId, Object settings, String symbol);
    /** Возвращает имя состояния, куда надо вернуться после выбора */
    String getReturnState();
}
