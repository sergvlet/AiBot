package com.chicu.aibot.bot.ui;

import com.chicu.aibot.bot.TelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class UiEventListener {

    private final TelegramBot bot;

    // кэш последнего payload по паре chatId:messageId
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
        String key = edit.getChatId() + ":" + edit.getMessageId();

        String payload = buildPayload(
                edit.getText(),
                (InlineKeyboardMarkup) edit.getReplyMarkup(),
                edit.getParseMode(),
                edit.getDisableWebPagePreview()
        );

        String prev = lastUiPayload.put(key, payload);
        if (payload.equals(prev)) {
            // ничего не поменялось — не дёргаем API
            return;
        }

        try {
            bot.execute(edit);
        } catch (TelegramApiRequestException ex) {
            String r = ex.getApiResponse();
            if (r != null && r.contains("message is not modified")) {
                log.debug("UI skip (no changes) {}", key);
                return;
            }
            log.warn("editMessageText failed: {}", ex.getMessage());
        } catch (TelegramApiException ex) {
            log.warn("editMessageText failed: {}", ex.getMessage());
        } catch (Exception ex) {
            log.warn("editMessageText failed: {}", ex.toString());
        }
    }

    private String buildPayload(String text,
                                InlineKeyboardMarkup markup,
                                String parseMode,
                                Boolean disablePreview) {
        StringBuilder sb = new StringBuilder();
        sb.append(text == null ? "" : text).append('|');
        sb.append(parseMode == null ? "" : parseMode).append('|');
        sb.append(Boolean.TRUE.equals(disablePreview)).append('|');
        sb.append(markup == null ? "null" : markup.toString());
        return sb.toString();
    }
}
