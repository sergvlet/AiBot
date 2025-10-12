package com.chicu.aibot.bot;

import com.chicu.aibot.bot.menu.core.MenuService;
import com.chicu.aibot.bot.menu.core.MenuSessionService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@SuppressWarnings("deprecation")
public class TelegramBot extends TelegramLongPollingBot {

    // практические лимиты телеги: 4096 hard, оставим запас под маркап
    private static final int HARD_LIMIT = 4096;
    private static final int SAFE_EDIT  = 3000;
    private static final int SAFE_SEND  = 3300;

    private final TelegramBotProperties props;
    private final MenuService menuService;
    private final MenuSessionService sessionService;

    @PostConstruct
    public void init() {
        try {
            new TelegramBotsApi(DefaultBotSession.class).registerBot(this);
            log.info("TelegramBot @{} зарегистрирован", props.getUsername());
        } catch (TelegramApiException e) {
            log.error("Не удалось зарегистрировать бота", e);
        }
    }

    @Override public String getBotUsername() { return props.getUsername(); }
    @Override @Deprecated public String getBotToken() { return props.getToken(); }

    @Override
    public void onUpdateReceived(Update update) {
        log.info("Получен update");
        Long chatId = extractChatId(update);

        menuService.popNotice(chatId).ifPresent(this::sendMessage);

        String state = menuService.handleInput(update);
        log.info("Переходим в состояние '{}'", state);

        SendMessage out = (SendMessage) menuService.renderState(state, chatId);

        if (update.hasCallbackQuery()) {
            Integer msgId = sessionService.getMenuMessageId(chatId);
            if (msgId != null) {
                editMessage(EditMessageText.builder()
                        .chatId(chatId.toString())
                        .messageId(msgId)
                        .text(out.getText())
                        .parseMode(out.getParseMode())
                        .replyMarkup((InlineKeyboardMarkup) out.getReplyMarkup())
                        .disableWebPagePreview(out.getDisableWebPagePreview())
                        .build());
                log.info("Сообщение {} отредактировано", msgId);
                return;
            }
        }

        Message sent = sendMessage(out);
        if (sent != null) {
            sessionService.setMenuMessageId(chatId, sent.getMessageId());
            log.info("Отправлено сообщение {}", sent.getMessageId());
        }
    }

    /* ===================== send/edit with fallbacks ===================== */

    private Message sendMessage(SendMessage msg) {
        if (textLen(msg.getText()) > SAFE_SEND) {
            return sendPaginated(msg);
        }
        try {
            return execute(msg);
        } catch (TelegramApiRequestException e) {
            if (isTooLongError(e)) {
                log.warn("sendMessage: MESSAGE_TOO_LONG — выполняю разбиение");
                return sendPaginated(msg);
            }
            if (isParseEntitiesError(e)) {
                try {
                    SendMessage safe = SendMessage.builder()
                            .chatId(msg.getChatId())
                            .text(stripMarkdown(msg.getText()))
                            .replyMarkup(msg.getReplyMarkup())
                            .disableWebPagePreview(msg.getDisableWebPagePreview())
                            .build();
                    return execute(safe);
                } catch (TelegramApiException ex2) {
                    log.error("Ошибка при отправке сообщения (fallback parse тоже не удался)", ex2);
                    return null;
                }
            }
            log.error("Ошибка при отправке сообщения", e);
            return null;
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке сообщения", e);
            return null;
        }
    }

    private void editMessage(EditMessageText edit) {
        if (textLen(edit.getText()) > SAFE_EDIT) {
            log.warn("editMessage: текст длиннее {} — выполняю разбиение без прямого execute(edit)", SAFE_EDIT);
            paginateEditFlow(edit);
            return;
        }
        try {
            execute(edit);
        } catch (TelegramApiRequestException e) {
            String api = e.getApiResponse();
            if (api != null && api.contains("message is not modified")) return;

            if (isTooLongError(e)) {
                log.warn("editMessage: MESSAGE_TOO_LONG — выполняю разбиение");
                paginateEditFlow(edit);
                return;
            }
            if (isParseEntitiesError(e)) {
                try {
                    EditMessageText safe = EditMessageText.builder()
                            .chatId(edit.getChatId())
                            .messageId(edit.getMessageId())
                            .text(stripMarkdown(edit.getText()))
                            .replyMarkup(edit.getReplyMarkup())
                            .disableWebPagePreview(edit.getDisableWebPagePreview())
                            .build();
                    execute(safe);
                    return;
                } catch (TelegramApiException ex2) {
                    log.error("Ошибка при редактировании (fallback parse тоже не удался)", ex2);
                    return;
                }
            }
            log.error("Ошибка при редактировании сообщения", e);
        } catch (TelegramApiException e) {
            log.error("Ошибка при редактировании сообщения", e);
        }
    }

    /** Разбивка длинного редактируемого сообщения: клавиатура ПЕРЕЕЗЖАЕТ на ПОСЛЕДНЮЮ часть. */
    private void paginateEditFlow(EditMessageText edit) {
        List<String> parts = paginate(edit.getText(), SAFE_EDIT);
        if (parts.isEmpty()) return;

        Long chatIdLong = parseChatId(edit.getChatId());
        Integer firstMessageId = edit.getMessageId();
        var originalMarkup = edit.getReplyMarkup();

        // 1) правим исходное сообщение первой частью БЕЗ клавиатуры
        try {
            EditMessageText first = EditMessageText.builder()
                    .chatId(edit.getChatId())
                    .messageId(firstMessageId)
                    .text(parts.getFirst())
                    .replyMarkup(null) // ключевой момент: убираем клавиатуру из «первой страницы»
                    .disableWebPagePreview(edit.getDisableWebPagePreview())
                    .build();
            execute(first);
        } catch (Exception ex) {
            log.error("Не удалось отредактировать первую часть: {}", ex.getMessage());
            // на всякий случай отправим всё новыми сообщениями; клаву — на последнюю
            Message last = null;
            for (int i = 0; i < parts.size(); i++) {
                last = safeExecute(SendMessage.builder()
                        .chatId(edit.getChatId())
                        .text(parts.get(i))
                        .replyMarkup(i == parts.size() - 1 ? originalMarkup : null)
                        .disableWebPagePreview(edit.getDisableWebPagePreview())
                        .build());
            }
            if (last != null && chatIdLong != null) {
                sessionService.setMenuMessageId(chatIdLong, last.getMessageId());
                log.info("Меню перенесено на новое сообщение {}", last.getMessageId());
            }
            return;
        }

        // 2) отправляем хвост новыми сообщениями; клавиатура — ТОЛЬКО у последней части
        Message last = null;
        for (int i = 1; i < parts.size(); i++) {
            last = safeExecute(SendMessage.builder()
                    .chatId(edit.getChatId())
                    .text(parts.get(i))
                    .replyMarkup(i == parts.size() - 1 ? originalMarkup : null)
                    .disableWebPagePreview(edit.getDisableWebPagePreview())
                    .build());
        }

        // 3) переносим «активное меню» на последнюю часть
        if (last != null && chatIdLong != null) {
            sessionService.setMenuMessageId(chatIdLong, last.getMessageId());
            log.info("Меню перенесено на новое сообщение {}", last.getMessageId());
        }
    }

    /* ===================== helpers ===================== */

    private Message safeExecute(SendMessage sm) {
        String text = sm.getText();
        if (textLen(text) > SAFE_SEND) {
            List<String> pages = paginate(text, SAFE_SEND);
            Message last = null;
            for (int i = 0; i < pages.size(); i++) {
                try {
                    last = execute(SendMessage.builder()
                            .chatId(sm.getChatId())
                            .text(pages.get(i))
                            .replyMarkup(i == pages.size() - 1 ? sm.getReplyMarkup() : null)
                            .disableWebPagePreview(sm.getDisableWebPagePreview())
                            .build());
                } catch (Exception ex) {
                    log.error("safeExecute: не удалось отправить часть {}: {}", i + 1, ex.getMessage());
                }
            }
            return last;
        }
        try {
            return execute(sm);
        } catch (Exception e) {
            log.error("safeExecute: ошибка при отправке: {}", e.getMessage());
            return null;
        }
    }

    private Long extractChatId(Update u) {
        if (u.hasCallbackQuery()) return u.getCallbackQuery().getMessage().getChatId();
        else if (u.hasMessage())   return u.getMessage().getChatId();
        throw new IllegalArgumentException("Cannot extract chatId");
    }

    private static Long parseChatId(String chatId) {
        try { return chatId == null ? null : Long.valueOf(chatId); }
        catch (NumberFormatException e) { return null; }
    }

    private static boolean isParseEntitiesError(TelegramApiRequestException e) {
        String r = e.getApiResponse(), m = e.getMessage();
        return (r != null && r.contains("can't parse entities")) || (m != null && m.contains("can't parse entities"));
    }

    private static boolean isTooLongError(TelegramApiRequestException e) {
        String r = e.getApiResponse(), m = e.getMessage();
        return (r != null && r.contains("MESSAGE_TOO_LONG"))
                || (m != null && m.contains("MESSAGE_TOO_LONG"))
                || (r != null && r.contains("Bad Request: MESSAGE_TOO_LONG"));
    }

    private static String stripMarkdown(String s) {
        if (s == null) return "";
        return s.replace("*","").replace("_","").replace("`","").replace("~","")
                .replace("<b>","").replace("</b>","")
                .replace("<i>","").replace("</i>","")
                .replace("<u>","").replace("</u>","")
                .replace("<s>","").replace("</s>","")
                .replace("<code>","").replace("</code>","")
                .replace("<pre>","").replace("</pre>","");
    }

    private static int textLen(String s) { return s == null ? 0 : s.length(); }

    private static List<String> paginate(String text, int maxLen) {
        List<String> parts = new ArrayList<>();
        if (text == null || text.isEmpty()) { parts.add(""); return parts; }
        int n = text.length(), i = 0;
        while (i < n) {
            int end = Math.min(i + maxLen, n);
            int cut = text.lastIndexOf('\n', end);
            if (cut < i + Math.max(100, maxLen / 3)) cut = end;
            parts.add(text.substring(i, cut));
            i = cut;
        }
        return parts;
    }

    /** Отправка длинного сообщения порциями. Клавиатура — на последнюю часть. */
    private Message sendPaginated(SendMessage original) {
        List<String> parts = paginate(original.getText(), TelegramBot.SAFE_SEND);
        Message last = null;
        for (int i = 0; i < parts.size(); i++) {
            try {
                SendMessage sm = SendMessage.builder()
                        .chatId(original.getChatId())
                        .text(parts.get(i))
                        .replyMarkup(i == parts.size() - 1 ? original.getReplyMarkup() : null)
                        .disableWebPagePreview(original.getDisableWebPagePreview())
                        .build();
                last = execute(sm);
            } catch (Exception ex) {
                log.error("sendPaginated: не удалось отправить часть {}: {}", i + 1, ex.getMessage());
            }
        }
        return last;
    }
}
