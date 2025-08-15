package com.chicu.aibot.bot.ui.impl;

import com.chicu.aibot.bot.menu.core.MenuSessionService;
import com.chicu.aibot.bot.menu.feature.ai.strategy.scalping.service.ScalpingPanelRenderer;
import com.chicu.aibot.bot.ui.UiAutorefreshService;
import com.chicu.aibot.bot.ui.UiEditMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.Map;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class UiAutorefreshServiceImpl implements UiAutorefreshService {

    private final MenuSessionService sessionService;
    private final ScalpingPanelRenderer scalpingPanel;
    private final ApplicationEventPublisher events;

    private ScheduledThreadPoolExecutor pool;
    private final Map<String, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        pool = new ScheduledThreadPoolExecutor(2);
        pool.setRemoveOnCancelPolicy(true);
    }

    @PreDestroy
    void stop() {
        pool.shutdownNow();
    }

    @Override
    public void enable(Long chatId, String panelName) {
        String key = key(chatId, panelName);
        ScheduledFuture<?> old = tasks.get(key);
        if (old != null && !old.isCancelled() && !old.isDone()) return;

        ScheduledFuture<?> fut = pool.scheduleAtFixedRate(() -> safeRefresh(chatId, panelName),
                10, 10, TimeUnit.SECONDS);
        tasks.put(key, fut);
        log.info("UI autorefresh enabled for {}:{}", chatId, panelName);
    }

    @Override
    public void disable(Long chatId, String panelName) {
        String key = key(chatId, panelName);
        ScheduledFuture<?> fut = tasks.remove(key);
        if (fut != null) fut.cancel(true);
        log.info("UI autorefresh disabled for {}:{}", chatId, panelName);
    }

    private void safeRefresh(Long chatId, String panelName) {
        try {
            Integer msgId = sessionService.getMenuMessageId(chatId);
            if (msgId == null) return;

            var panel = scalpingPanel.render(chatId); // уже содержит и текст, и клавиатуру
            var text  = panel.getText();
            var kb    = (InlineKeyboardMarkup) panel.getReplyMarkup();

            events.publishEvent(new UiEditMessageEvent(this, chatId, msgId, text, "Markdown", kb));
        } catch (Exception e) {
            log.debug("Autorefresh error {}:{} — {}", chatId, panelName, e.getMessage());
        }
    }

    private static String key(Long chatId, String panelName) { return chatId + ":" + panelName; }
}
