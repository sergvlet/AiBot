package com.chicu.aibot.bot.ui;

public interface UiAutorefreshService {
    void enable(Long chatId, String panelName);   // запускаем периодический апдейт (10–15 сек)
    void disable(Long chatId, String panelName);  // останавливаем
}
