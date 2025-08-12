// com.chicu.aibot.bot.ui.UiEditMessageEvent
package com.chicu.aibot.bot.ui;

import lombok.Getter;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

@Getter
public class UiEditMessageEvent {
    private final Object source;
    private final Long chatId;
    private final Integer messageId;
    private final String text;
    private final String parseMode;
    private final InlineKeyboardMarkup replyMarkup; // NEW

    public UiEditMessageEvent(Object source, Long chatId, Integer messageId, String text, String parseMode) {
        this(source, chatId, messageId, text, parseMode, null);
    }

    public UiEditMessageEvent(Object source, Long chatId, Integer messageId, String text,
                              String parseMode, InlineKeyboardMarkup replyMarkup) {
        this.source = source;
        this.chatId = chatId;
        this.messageId = messageId;
        this.text = text;
        this.parseMode = parseMode;
        this.replyMarkup = replyMarkup;
    }
}
