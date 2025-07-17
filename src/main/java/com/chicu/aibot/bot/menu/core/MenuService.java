package com.chicu.aibot.bot.menu.core;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MenuService {

    public static final String MAIN_MENU = "main";

    private final MenuSessionService sessionService;
    private final List<MenuState> states;

    /** Ключ → состояние, заполняется в init() */
    private Map<String, MenuState> stateMap;

    @PostConstruct
    private void init() {
        stateMap = states.stream()
                .collect(Collectors.toUnmodifiableMap(MenuState::name, s -> s));
        log.info("MenuService: зарегистрированы состояния {}", stateMap.keySet());
    }

    public Optional<SendMessage> popNotice(Long chatId) {
        return Optional.empty();
    }

    public String handleInput(Update update) {
        Long chatId = extractChatId(update);

        // 1) получаем текущее состояние или MAIN_MENU по умолчанию
        String current = Optional.ofNullable(sessionService.getCurrentState(chatId))
                .orElse(MAIN_MENU);

        // 2) вычисляем следующее состояние
        MenuState handler = stateMap.getOrDefault(current, stateMap.get(MAIN_MENU));
        String next = handler.handleInput(update);

        // 3) если неизвестное — сбрасываем
        if (!stateMap.containsKey(next)) {
            log.warn("Unknown next state '{}', сбрасываем в MAIN_MENU", next);
            next = MAIN_MENU;
        }

        // 4) сохраняем
        sessionService.setCurrentState(chatId, next);
        return next;
    }

    public SendMessage renderState(String state, Long chatId) {
        MenuState ms = stateMap.get(state);
        if (ms == null) {
            log.warn("Unknown state in renderState: {}", state);
            ms = stateMap.get(MAIN_MENU);
        }
        return ms.render(chatId);
    }

    private Long extractChatId(Update u) {
        if (u.hasCallbackQuery()) {
            return u.getCallbackQuery().getMessage().getChatId();
        } else if (u.hasMessage()) {
            return u.getMessage().getChatId();
        }
        throw new IllegalArgumentException("Cannot extract chatId from Update");
    }
}
