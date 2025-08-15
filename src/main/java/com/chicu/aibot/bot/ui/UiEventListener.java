package com.chicu.aibot.bot.ui;

import com.chicu.aibot.bot.TelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class UiEventListener {

    private final TelegramBot bot;

    /** Кэш последнего payload по паре chatId:messageId. */
    private final Map<String, String> lastUiPayload = new ConcurrentHashMap<>();

    @EventListener
    public void onUiEdit(UiEditMessageEvent e) {
        EditMessageText edit = EditMessageText.builder()
                .chatId(e.getChatId().toString())
                .messageId(e.getMessageId())
                .text(e.getText())
                .parseMode(e.getParseMode())
                .replyMarkup(e.getReplyMarkup())
                .build();
        safeEdit(bot, edit);
    }

    /** Отправляет edit только если контент изменился; гасит "message is not modified". */
    private void safeEdit(TelegramBot bot, EditMessageText edit) {
        final String key = buildKey(edit.getChatId(), edit.getMessageId());

        final String payload = buildPayload(
                edit.getText(),
                edit.getReplyMarkup(),
                edit.getParseMode(),
                edit.getDisableWebPagePreview()
        );

        String prev = lastUiPayload.get(key);
        if (payload.equals(prev)) {
            return;
        }

        try {
            bot.execute(edit);
            lastUiPayload.put(key, payload);
        } catch (Exception ex) {
            if (isNotModified(ex)) {
                lastUiPayload.put(key, payload);
                log.debug("UI skip (no changes) {}", key);
                return;
            }
            log.warn("editMessageText failed: {}", safeMessage(ex));
        }
    }

    private static String buildKey(String chatId, Integer messageId) {
        return chatId + ":" + messageId;
    }

    private static boolean isNotModified(Throwable ex) {
        if (ex instanceof TelegramApiRequestException req) {
            String r = req.getApiResponse();
            return r != null && r.contains("message is not modified");
        }
        return false;
    }

    private static String safeMessage(Throwable ex) {
        String m = ex.getMessage();
        return (m == null || m.isBlank()) ? ex.toString() : m;
    }

    private String buildPayload(String text,
                                InlineKeyboardMarkup markup,
                                String parseMode,
                                Boolean disablePreview) {
        return (text == null ? "" : text) + '|'
                + (parseMode == null ? "" : parseMode) + '|'
                + Boolean.TRUE.equals(disablePreview) + '|'
                + (markup == null ? "null" : markup.toString());
    }
}
