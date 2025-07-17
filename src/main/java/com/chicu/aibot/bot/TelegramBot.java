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
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Slf4j
@Component
@RequiredArgsConstructor
@SuppressWarnings("deprecation") // для getBotToken() и устаревшего конструктора суперкласса
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

        // 1) одноразовое уведомление
        menuService.popNotice(chatId).ifPresent(this::sendMessage);

        // 2) новое состояние
        String state = menuService.handleInput(update);
        log.info("Переходим в состояние '{}'", state);

        // 3) рендер
        SendMessage out = menuService.renderState(state, chatId);

        // 4) callback → редактируем, если есть старое меню
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

        // 5) иначе — отправляем новое меню
        Message sent = sendMessage(out);
        if (sent != null) {
            sessionService.setMenuMessageId(chatId, sent.getMessageId());
            log.info("Отправлено сообщение {}", sent.getMessageId());
        }
    }

    private Message sendMessage(SendMessage msg) {
        try {
            return execute(msg);
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке сообщения", e);
            return null;
        }
    }

    private void editMessage(EditMessageText edit) {
        try {
            execute(edit);
        } catch (TelegramApiException e) {
            if (!(e instanceof org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException
                    && e.getMessage().contains("message is not modified"))) {
                log.error("Ошибка при редактировании сообщения", e);
            }
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
}
