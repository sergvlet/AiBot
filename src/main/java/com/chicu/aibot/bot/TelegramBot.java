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

@Slf4j
@Component
@RequiredArgsConstructor
@SuppressWarnings("deprecation")
public class TelegramBot extends TelegramLongPollingBot {

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

    @Override
    public String getBotUsername() {
        return props.getUsername();
    }

    @Override
    @Deprecated
    public String getBotToken() {
        return props.getToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        log.info("Получен update");
        Long chatId = extractChatId(update);

        menuService.popNotice(chatId).ifPresent(this::sendMessage);

        String state = menuService.handleInput(update);
        log.info("Переходим в состояние '{}'", state);

        SendMessage out = menuService.renderState(state, chatId);

        if (update.hasCallbackQuery()) {
            Integer msgId = sessionService.getMenuMessageId(chatId);
            if (msgId != null) {
                editMessage(EditMessageText.builder()
                        .chatId(chatId.toString())
                        .messageId(msgId)
                        .text(out.getText())
                        .parseMode(out.getParseMode())
                        .replyMarkup((InlineKeyboardMarkup) out.getReplyMarkup())
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

    private Message sendMessage(SendMessage msg) {
        try {
            return execute(msg);
        } catch (TelegramApiRequestException e) {
            // Фолбэк на «can't parse entities»: шлём текст без Markdown/HTML
            if (isParseEntitiesError(e)) {
                try {
                    SendMessage safe = SendMessage.builder()
                            .chatId(msg.getChatId())
                            .text(stripMarkdown(msg.getText()))
                            .replyMarkup(msg.getReplyMarkup())
                            .disableWebPagePreview(msg.getDisableWebPagePreview())
                            .build(); // без parseMode
                    return execute(safe);
                } catch (TelegramApiException ex2) {
                    log.error("Ошибка при отправке сообщения (fallback тоже не удался)", ex2);
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
        try {
            execute(edit);
        } catch (TelegramApiRequestException e) {
            // «message is not modified» — игнорируем
            if (e.getApiResponse() != null && e.getApiResponse().contains("message is not modified")) {
                return;
            }
            // Фолбэк на «can't parse entities»: шлём без parseMode и с очищенным текстом
            if (isParseEntitiesError(e)) {
                try {
                    EditMessageText safe = EditMessageText.builder()
                            .chatId(edit.getChatId())
                            .messageId(edit.getMessageId())
                            .text(stripMarkdown(edit.getText()))
                            .replyMarkup(edit.getReplyMarkup())
                            .disableWebPagePreview(edit.getDisableWebPagePreview())
                            .build(); // без parseMode
                    execute(safe);
                    return;
                } catch (TelegramApiException ex2) {
                    log.error("Ошибка при редактировании сообщения (fallback тоже не удался)", ex2);
                    return;
                }
            }
            log.error("Ошибка при редактировании сообщения", e);
        } catch (TelegramApiException e) {
            log.error("Ошибка при редактировании сообщения", e);
        }
    }

    private Long extractChatId(Update u) {
        if (u.hasCallbackQuery()) {
            return u.getCallbackQuery().getMessage().getChatId();
        } else if (u.hasMessage()) {
            return u.getMessage().getChatId();
        }
        throw new IllegalArgumentException("Cannot extract chatId");
    }

    // ===== helpers =====

    /** Узнаём специфическую телеграм-ошибку «can't parse entities». */
    private static boolean isParseEntitiesError(TelegramApiRequestException e) {
        String r = e.getApiResponse();
        String m = e.getMessage();
        return (r != null && r.contains("can't parse entities"))
               || (m != null && m.contains("can't parse entities"));
    }

    /** Простой "чистильщик" Markdown/HTML-символов для безопасной повторной отправки. */
    private static String stripMarkdown(String s) {
        if (s == null) return "";
        return s
                .replace("*", "")
                .replace("_", "")
                .replace("`", "")
                .replace("~", "")
                .replace("<b>", "").replace("</b>", "")
                .replace("<i>", "").replace("</i>", "")
                .replace("<u>", "").replace("</u>", "")
                .replace("<s>", "").replace("</s>", "")
                .replace("<code>", "").replace("</code>", "")
                .replace("<pre>", "").replace("</pre>", "");
    }
}
