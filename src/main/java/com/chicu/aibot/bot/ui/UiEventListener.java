package com.chicu.aibot.bot.ui;

import com.chicu.aibot.bot.TelegramBot;
import com.chicu.aibot.bot.menu.core.MenuSessionService;
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

    /** Безопасный лимит для редактирования; длиннее — не редактируем отсюда (пусть TelegramBot делает пагинацию). */
    private static final int SAFE_EDIT = 3000;

    private final TelegramBot bot;
    private final MenuSessionService sessionService;

    /** Кэш последнего payload по паре chatId:messageId. */
    private final Map<String, String> lastUiPayload = new ConcurrentHashMap<>();

    @EventListener
    public void onUiEdit(UiEditMessageEvent e) {
        // 1) Узнаём актуальный messageId меню (мог «переехать» при пагинации)
        Integer currentMsgId = sessionService.getMenuMessageId(e.getChatId());
        if (currentMsgId == null) {
            // если в сессии нет — используем тот, что пришёл в событии (на первый раз)
            currentMsgId = e.getMessageId();
        }

        // 2) Если текст слишком длинный — не редактируем тут, это задача TelegramBot (он умеет делить)
        if (len(e.getText()) > SAFE_EDIT) {
            log.debug("UiEventListener: skip edit (text>{}) for chatId={}, msgId={}", SAFE_EDIT, e.getChatId(), currentMsgId);
            return;
        }

        EditMessageText edit = EditMessageText.builder()
                .chatId(e.getChatId().toString())
                .messageId(currentMsgId)
                .text(e.getText())
                .parseMode(e.getParseMode())
                .replyMarkup(e.getReplyMarkup())
                .build();

        safeEditInternal(edit, e.getChatId());
    }

    /** Отправляет edit только если контент изменился; гасит «message is not modified» и MESSAGE_TOO_LONG. */
    private void safeEditInternal(EditMessageText edit, Long chatId) {
        final String key = buildKey(edit.getChatId(), edit.getMessageId());
        final String payload = buildPayload(
                edit.getText(),
                edit.getReplyMarkup(),
                edit.getParseMode(),
                edit.getDisableWebPagePreview()
        );

        String prev = lastUiPayload.get(key);
        if (payload.equals(prev)) {
            // Нечего менять — не шлём запросы
            log.debug("UiEventListener: no changes for {}", key);
            return;
        }

        try {
            bot.execute(edit);
            lastUiPayload.put(key, payload);
        } catch (TelegramApiRequestException ex) {
            if (isNotModified(ex)) {
                // Ок, считаем применённым, чтобы не спамить
                lastUiPayload.put(key, payload);
                log.debug("UiEventListener: message is not modified — skip {}", key);
                return;
            }
            if (isTooLong(ex)) {
                // После «переезда»/пагинации это штатно — НЕ ретраим отсюда
                log.debug("UiEventListener: MESSAGE_TOO_LONG — skip {}, pagination is handled by TelegramBot", key);
                return;
            }
            log.warn("UiEventListener: editMessageText failed: {}", safeMessage(ex));
        } catch (TelegramApiException ex) {
            log.warn("UiEventListener: editMessageText failed: {}", safeMessage(ex));
        } catch (Exception ex) {
            log.warn("UiEventListener: unexpected error: {}", safeMessage(ex), ex);
        }
    }

    /* ===== helpers ===== */

    private static int len(String s) { return s == null ? 0 : s.length(); }

    private static String buildKey(String chatId, Integer messageId) {
        return chatId + ":" + messageId;
    }

    private static boolean isNotModified(TelegramApiRequestException req) {
        String r = req.getApiResponse(), m = req.getMessage();
        return (r != null && r.contains("message is not modified")) || (m != null && m.contains("message is not modified"));
    }

    private static boolean isTooLong(TelegramApiRequestException req) {
        String r = req.getApiResponse(), m = req.getMessage();
        return (r != null && r.contains("MESSAGE_TOO_LONG"))
               || (m != null && m.contains("MESSAGE_TOO_LONG"))
               || (r != null && r.contains("Bad Request: MESSAGE_TOO_LONG"));
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
