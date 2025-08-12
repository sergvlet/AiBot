package com.chicu.aibot.trading.scheduler.impl;

import com.chicu.aibot.bot.TelegramBot;
import com.chicu.aibot.bot.menu.core.MenuSessionService;
import com.chicu.aibot.bot.menu.feature.ai.strategy.scalping.ScalpingConfigState;
import com.chicu.aibot.bot.menu.feature.ai.strategy.scalping.service.ScalpingPanelRenderer;
import com.chicu.aibot.strategy.StrategyRegistry;
import com.chicu.aibot.strategy.TradingStrategy;
import com.chicu.aibot.strategy.fibonacci.model.FibonacciGridStrategySettings;
import com.chicu.aibot.strategy.fibonacci.repository.FibonacciGridStrategySettingsRepository;
import com.chicu.aibot.strategy.scalping.model.ScalpingStrategySettings;
import com.chicu.aibot.strategy.scalping.repository.ScalpingStrategySettingsRepository;
import com.chicu.aibot.trading.scheduler.SchedulerService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulerServiceImpl implements SchedulerService {

    private final StrategyRegistry registry;
    private final ScalpingStrategySettingsRepository scalpingRepo;
    private final FibonacciGridStrategySettingsRepository fibRepo;

    // --- автообновление UI ---
    // ВАЖНО: чтобы не создать цикл, берём бота через ленивый провайдер
    private final ObjectProvider<TelegramBot> botProvider;
    private final MenuSessionService sessionService;
    private final ScalpingPanelRenderer scalpingPanelRenderer;

    @Value("${ui.autorefresh.ms:1000}")
    private long uiAutorefreshMs;

    private ScheduledThreadPoolExecutor scheduler;
    private final Map<String, ScheduledFuture<?>> runningTasks = new ConcurrentHashMap<>();
    private ScheduledFuture<?> uiRefreshFuture;

    // отключённые панели (key = "<chatId>:<strategy>")
    private final Set<String> uiAutorefreshDisabled = ConcurrentHashMap.newKeySet();

    @PostConstruct
    private void init() {
        int threads = Math.max(2, Runtime.getRuntime().availableProcessors());
        this.scheduler = new ScheduledThreadPoolExecutor(threads);
        this.scheduler.setRemoveOnCancelPolicy(true);
        log.info("Планировщик инициализирован: {} поток(а/ов)", threads);

        // глобальная периодическая задача обновления UI
        startUiAutorefreshIfNeeded();
    }

    @PreDestroy
    private void shutdown() {
        log.info("Останавливаю планировщик…");
        if (uiRefreshFuture != null) uiRefreshFuture.cancel(true);
        scheduler.shutdownNow();
    }

    @Override
    public void startStrategy(Long chatId, String strategyName) {
        String key = buildKey(chatId, strategyName);

        ScheduledFuture<?> existing = runningTasks.get(key);
        if (existing != null && (existing.isCancelled() || existing.isDone())) {
            runningTasks.remove(key);
            existing = null;
        }
        if (existing != null) {
            log.info("Стратегия {} уже запущена для chatId={}", strategyName, chatId);
            return;
        }

        long intervalSec = resolveIntervalSec(chatId, strategyName);
        TradingStrategy strategy = registry.getStrategyOrThrow(strategyName);

        try {
            strategy.start(chatId);
        } catch (Exception e) {
            log.error("Ошибка start() у стратегии {} @{}: {}", strategyName, chatId, e.getMessage(), e);
            throw e;
        }

        ScheduledFuture<?> future = scheduleLoop(chatId, strategyName, strategy, intervalSec);
        runningTasks.put(key, future);
        log.info("Запущена {} для chatId={} (интервал={}s)", strategyName, chatId, intervalSec);
    }

    @Override
    public void stopStrategy(Long chatId, String strategyName) {
        String key = buildKey(chatId, strategyName);
        ScheduledFuture<?> future = runningTasks.remove(key);

        if (future == null || future.isCancelled() || future.isDone()) {
            log.info("Стратегия {} не запущена для chatId={}; останавливать нечего", strategyName, chatId);
            return;
        }

        future.cancel(true);
        try {
            registry.getStrategyOrThrow(strategyName).stop(chatId);
        } catch (Exception e) {
            log.error("Ошибка stop() у стратегии {} @{}: {}", strategyName, chatId, e.getMessage(), e);
        }
        log.info("Остановлена {} для chatId={}", strategyName, chatId);
    }

    /** Перезапустить стратегию с актуальным тайм фреймом. */
    public void restartStrategy(Long chatId, String strategyName) {
        String key = buildKey(chatId, strategyName);
        TradingStrategy strategy = registry.getStrategyOrThrow(strategyName);

        ScheduledFuture<?> old = runningTasks.remove(key);
        if (old != null && !old.isCancelled() && !old.isDone()) {
            old.cancel(true);
            try {
                strategy.stop(chatId);
            } catch (Exception e) {
                log.error("Ошибка stop() при перезапуске {} @{}: {}", strategyName, chatId, e.getMessage(), e);
            }
            log.info("Старая задача {} для chatId={} отменена", strategyName, chatId);
        } else {
            log.info("Стратегия {} для chatId={} не была запущена — перезапускаю как новую", strategyName, chatId);
        }

        long intervalSec = resolveIntervalSec(chatId, strategyName);
        try {
            strategy.start(chatId);
        } catch (Exception e) {
            log.error("Ошибка start() при перезапуске {} @{}: {}", strategyName, chatId, e.getMessage(), e);
            throw e;
        }

        ScheduledFuture<?> future = scheduleLoop(chatId, strategyName, strategy, intervalSec);
        runningTasks.put(key, future);
        log.info("Перезапущена {} для chatId={} (интервал={}s)", strategyName, chatId, intervalSec);
    }

    @Override
    public boolean isStrategyActive(Long chatId, String strategyName) {
        String key = buildKey(chatId, strategyName);
        ScheduledFuture<?> f = runningTasks.get(key);
        return f != null && !f.isDone() && !f.isCancelled();
    }

    // === управление авто обновлением UI ===



    // ==================== внутренняя логика ====================

    private ScheduledFuture<?> scheduleLoop(Long chatId, String strategyName, TradingStrategy strategy, long intervalSec) {
        return scheduler.scheduleAtFixedRate(() -> {
            try {
                double price = strategy.getCurrentPrice(chatId);
                strategy.onPriceUpdate(chatId, price);
            } catch (Exception e) {
                log.error("Ошибка onPriceUpdate для {} @{}: {}", strategyName, chatId, e.getMessage(), e);
            }
        }, 0, intervalSec, TimeUnit.SECONDS);
    }

    private long resolveIntervalSec(Long chatId, String strategyName) {
        String tf;
        switch (strategyName) {
            case "SCALPING" -> {
                ScalpingStrategySettings s = scalpingRepo.findById(chatId)
                        .orElseThrow(() -> new IllegalStateException("Scalping settings not found for chatId=" + chatId));
                tf = s.getTimeframe();
            }
            case "FIBONACCI_GRID" -> {
                FibonacciGridStrategySettings f = fibRepo.findById(chatId)
                        .orElseThrow(() -> new IllegalStateException("FibonacciGrid settings not found for chatId=" + chatId));
                tf = f.getTimeframe();
            }
            default -> throw new IllegalArgumentException("Unknown strategy: " + strategyName);
        }
        return parseTimeframe(tf);
    }

    private long parseTimeframe(String tfRaw) {
        if (tfRaw == null || tfRaw.isBlank()) {
            throw new IllegalArgumentException("Пустой timeframe");
        }
        String tf = tfRaw.trim().toLowerCase();
        char unit = tf.charAt(tf.length() - 1);
        String num = tf.substring(0, tf.length() - 1);
        long value;
        try {
            value = Long.parseLong(num);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Некорректное число в timeframe: " + tfRaw, e);
        }
        return switch (unit) {
            case 's' -> Duration.ofSeconds(value).getSeconds();
            case 'm' -> Duration.ofMinutes(value).getSeconds();
            case 'h' -> Duration.ofHours(value).getSeconds();
            case 'd' -> Duration.ofDays(value).getSeconds();
            default -> throw new IllegalArgumentException("Неизвестная единица timeframe '" + unit + "' в " + tfRaw);
        };
    }

    private String buildKey(Long chatId, String strategyName) {
        return chatId + ":" + strategyName;
    }

    private void startUiAutorefreshIfNeeded() {
        if (uiRefreshFuture == null || uiRefreshFuture.isCancelled() || uiRefreshFuture.isDone()) {
            uiRefreshFuture = scheduler.scheduleAtFixedRate(
                    this::refreshScalpingPanelsSafe,
                    uiAutorefreshMs,
                    uiAutorefreshMs,
                    TimeUnit.MILLISECONDS
            );
            log.info("UI автообновление включено каждые {} мс", uiAutorefreshMs);
        }
    }

    // ==================== автообновление UI скальпинга ====================

    private void refreshScalpingPanelsSafe() {
        try {
            refreshScalpingPanels();
        } catch (Exception e) {
            log.debug("UI autorefresh tick failed: {}", e.getMessage());
        }
    }

    private void refreshScalpingPanels() {
        if (runningTasks.isEmpty()) return;

        Set<String> keys = Set.copyOf(runningTasks.keySet());
        for (String key : keys) {
            if (!key.endsWith(":SCALPING")) continue;
            if (uiAutorefreshDisabled.contains(key)) continue;

            Long chatId = extractChatId(key);
            if (chatId == null) continue;

            String currentState = tryGetCurrentState(chatId);
            if (!ScalpingConfigState.NAME.equals(currentState)) continue;

            Integer messageId = tryGetLastMessageId(chatId);
            if (messageId == null) continue;

            try {
                SendMessage sm = scalpingPanelRenderer.render(chatId);

                EditMessageText edit = EditMessageText.builder()
                        .chatId(chatId.toString())
                        .messageId(messageId)
                        .text(sm.getText())
                        .parseMode(sm.getParseMode())
                        .disableWebPagePreview(Boolean.TRUE.equals(sm.getDisableWebPagePreview()))
                        .replyMarkup((InlineKeyboardMarkup) sm.getReplyMarkup())
                        .build();

                TelegramBot bot = botProvider.getIfAvailable();
                if (bot != null) {
                    bot.execute(edit);
                }
            } catch (Exception e) {
                log.debug("Не удалось обновить UI для chatId={}: {}", chatId, e.getMessage());
            }
        }
    }

    private Long extractChatId(String key) {
        int idx = key.indexOf(':');
        if (idx <= 0) return null;
        try {
            return Long.parseLong(key.substring(0, idx));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String tryGetCurrentState(Long chatId) {
        try {
            Method m = sessionService.getClass().getMethod("getCurrentState", Long.class);
            Object r = m.invoke(sessionService, chatId);
            return r == null ? null : r.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer tryGetLastMessageId(Long chatId) {
        for (String name : new String[]{"getLastMessageId", "getLastBotMessageId", "getMessageId"}) {
            try {
                Method m = sessionService.getClass().getMethod(name, Long.class);
                Object r = m.invoke(sessionService, chatId);
                if (r instanceof Integer i) return i;
                if (r instanceof Number n) return n.intValue();
            } catch (NoSuchMethodException ignore) {
                // пробуем следующее имя
            } catch (Exception e) {
                log.debug("Доступ к {} через {} не удался: {}", sessionService.getClass().getSimpleName(), name, e.getMessage());
            }
        }
        return null;
    }
}
