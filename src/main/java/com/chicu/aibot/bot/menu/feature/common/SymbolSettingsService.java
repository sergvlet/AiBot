package com.chicu.aibot.bot.menu.feature.common;

public interface SymbolSettingsService {
    /**  Создаём пустые настройки по chatId */
    Object getOrCreate(Long chatId);
    /** Сохраняем выбранный символ в этих настройках */
    void saveSymbol(Long chatId, Object settings, String symbol);
    /** Возвращает имя состояния, куда надо вернуться после выбора */
    String getReturnState();
}
