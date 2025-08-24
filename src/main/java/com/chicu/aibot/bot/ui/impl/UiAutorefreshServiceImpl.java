package com.chicu.aibot.bot.ui.impl;

import com.chicu.aibot.bot.menu.core.MenuService;
import com.chicu.aibot.bot.menu.core.MenuSessionService;
import com.chicu.aibot.bot.ui.UiAutorefreshService;
import com.chicu.aibot.bot.ui.UiEditMessageEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;

/**
 * Универсальный авто обновлятор UI-панелей.
 * - Без прямой зависимости от MenuService (через ObjectProvider) — нет цикла бинов.
 * - Публикует UiEditMessageEvent через стабильный конструктор.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UiAutorefreshServiceImpl implements UiAutorefreshService {

    private static final long REFRESH_INITIAL_DELAY_MS = 1000L;
    private static final long REFRESH_PERIOD_MS        = 1000L;

    private final MenuSessionService sessionService;
    private final ApplicationEventPublisher events;
    private final ObjectProvider<MenuService> menuServiceProvider;

    private ScheduledExecutorService scheduler;

    /** по одному джобу на чат */
    private final Map<Long, ScheduledFuture<?>> jobs = new ConcurrentHashMap<>();
    /** Активная панель на чат (тики чужих панелей игнорируются) */
    private final Map<Long, String> activePanel = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        ScheduledThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(2);
        pool.setRemoveOnCancelPolicy(true);
        this.scheduler = pool;
        log.info("UI autorefresh initialized (period={} ms)", REFRESH_PERIOD_MS);
    }

    @PreDestroy
    void stop() {
        try {
            scheduler.shutdownNow();
        } catch (Exception ignore) {
        }
    }

    @Override
    public void enable(Long chatId, String panelName) {
        if (chatId == null || panelName == null || panelName.isBlank()) return;

        cancelJob(chatId);
        activePanel.put(chatId, panelName);

        ScheduledFuture<?> fut = scheduler.scheduleAtFixedRate(
                () -> safeRefresh(chatId, panelName),
                REFRESH_INITIAL_DELAY_MS,
                REFRESH_PERIOD_MS,
                TimeUnit.MILLISECONDS
        );
        jobs.put(chatId, fut);
        log.debug("UI autorefresh ENABLED chatId={} panel='{}'", chatId, panelName);
    }

    @Override
    public void disable(Long chatId, String panelName) {
        if (chatId == null) return;

        String active = activePanel.get(chatId);
        if (!Objects.equals(active, panelName)) return; // уже переключились — ничего не делаем

        cancelJob(chatId);
        activePanel.remove(chatId);
        log.debug("UI autorefresh DISABLED chatId={} panel='{}'", chatId, panelName);
    }

    private void cancelJob(Long chatId) {
        ScheduledFuture<?> old = jobs.remove(chatId);
        if (old != null) old.cancel(true);
    }

    private void safeRefresh(Long chatId, String expectedPanel) {
        try {
            // если пользователь ушёл на другую панель — пропускаем тик
            String current = activePanel.get(chatId);
            if (!Objects.equals(current, expectedPanel)) return;

            Integer msgId = sessionService.getMenuMessageId(chatId);
            if (msgId == null) return;

            MenuService menuService = menuServiceProvider.getIfAvailable();
            if (menuService == null) return;

            // перерисовываем именно ожидаемую панель
            SendMessage view = menuService.renderState(expectedPanel, chatId);
            if (view == null) return;

            String text = view.getText();
            String parseMode = view.getParseMode() == null ? "Markdown" : view.getParseMode();
            InlineKeyboardMarkup kb = (InlineKeyboardMarkup) view.getReplyMarkup();

            // публикуем событие (конструктор соответствует UiEventListener)
            events.publishEvent(new UiEditMessageEvent(
                    this,
                    chatId,
                    msgId,
                    text,
                    parseMode,
                    kb
            ));
        } catch (Exception e) {
            log.debug("Autorefresh error {}:{} — {}", chatId, expectedPanel, e.getMessage());
        }
    }
}
