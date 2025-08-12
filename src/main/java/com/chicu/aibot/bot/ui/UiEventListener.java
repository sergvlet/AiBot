package com.chicu.aibot.bot.ui;

import com.chicu.aibot.bot.TelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Component
@RequiredArgsConstructor
@Slf4j
public class UiEventListener {

    private final TelegramBot bot; // инжектим вашего бота

    @EventListener
    public void onUiEdit(UiEditMessageEvent e) {
        EditMessageText edit = EditMessageText.builder()
                .chatId(e.getChatId().toString())
                .messageId(e.getMessageId())
                .text(e.getText())
                .parseMode(e.getParseMode())
                .replyMarkup(e.getReplyMarkup())
                .build();
        try {
            bot.execute(edit); // <-- ВАЖНО: вызываем у бота
        } catch (TelegramApiException ex) {
            log.warn("editMessageText failed: {}", ex.getMessage());
        }
    }
}
