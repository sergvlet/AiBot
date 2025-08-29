package com.chicu.aibot.bot.menu.core;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MenuService {

    public static final String MAIN_MENU = "main";

    private final MenuSessionService sessionService;
    private final List<MenuState> states;

    /** Хранилище всплывающих уведомлений */
    private final Map<Long, String> notices = new HashMap<>();

    /** Имя → объект состояния */
    private Map<String, MenuState> stateMap;

    @PostConstruct
    private void init() {
        stateMap = states.stream()
                .collect(Collectors.toUnmodifiableMap(MenuState::name, s -> s));
        log.info("✅ MenuService: зарегистрированы состояния: {}", stateMap.keySet());
    }

    /** Установить отложенное уведомление */
    public void deferNotice(Long chatId, String message) {
        notices.put(chatId, message);
    }

    /** Извлечь и удалить уведомление */
    public Optional<SendMessage> popNotice(Long chatId) {
        String notice = notices.remove(chatId);
        if (notice == null) return Optional.empty();

        return Optional.of(
                SendMessage.builder()
                        .chatId(chatId.toString())
                        .text(notice)
                        .build()
        );
    }

    /** Обработка входного обновления */
    public String handleInput(Update update) {
        Long chatId = extractChatId(update);
        String current = Optional.ofNullable(sessionService.getCurrentState(chatId))
                .orElse(MAIN_MENU);
        MenuState handler = stateMap.getOrDefault(current, stateMap.get(MAIN_MENU));
        String next = handler.handleInput(update);
        if (!stateMap.containsKey(next)) {
            log.warn("Unknown next state '{}', сбрасываем в MAIN_MENU", next);
            next = MAIN_MENU;
        }
        sessionService.setCurrentState(chatId, next);
        return next;
    }

    /** Отрисовать состояние по имени */
    public Object renderState(String state, Long chatId) {
        MenuState ms = stateMap.get(state);
        if (ms == null) {
            log.warn("Unknown state in renderState: {}", state);
            ms = stateMap.get(MAIN_MENU);
        }
        return ms.render(chatId);
    }

    /** 👉 Получить состояние по имени */
    public MenuState findState(String state) {
        return stateMap.get(state);
    }

    private Long extractChatId(Update u) {
        if (u.hasCallbackQuery()) {
            return u.getCallbackQuery().getMessage().getChatId();
        } else if (u.hasMessage()) {
            return u.getMessage().getChatId();
        }
        throw new IllegalArgumentException("❌ Невозможно извлечь chatId из Update");
    }
}
