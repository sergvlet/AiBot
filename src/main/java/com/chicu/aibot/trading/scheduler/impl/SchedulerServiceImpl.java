package com.chicu.aibot.trading.scheduler.impl;

import com.chicu.aibot.bot.TelegramBot;
import com.chicu.aibot.bot.menu.core.MenuSessionService;
import com.chicu.aibot.bot.menu.feature.ai.strategy.bollinger.BollingerConfigState;
import com.chicu.aibot.bot.menu.feature.ai.strategy.bollinger.service.BollingerPanelRenderer;
import com.chicu.aibot.bot.menu.feature.ai.strategy.fibonacci.FibonacciGridConfigState;
import com.chicu.aibot.bot.menu.feature.ai.strategy.fibonacci.service.FibonacciGridPanelRenderer;
import com.chicu.aibot.bot.menu.feature.ai.strategy.scalping.ScalpingConfigState;
import com.chicu.aibot.bot.menu.feature.ai.strategy.scalping.service.ScalpingPanelRenderer;
import com.chicu.aibot.strategy.StrategyRegistry;
import com.chicu.aibot.strategy.TradingStrategy;
import com.chicu.aibot.strategy.bollinger.model.BollingerStrategySettings;
import com.chicu.aibot.strategy.bollinger.repository.BollingerStrategySettingsRepository;
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
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulerServiceImpl implements SchedulerService {

    private final StrategyRegistry registry;

    // репозитории настроек
    private final ScalpingStrategySettingsRepository scalpingRepo;
    private final FibonacciGridStrategySettingsRepository fibRepo;
    private final BollingerStrategySettingsRepository bollRepo;

    private final ObjectProvider<ScalpingPanelRenderer> scalpingPanel;
    private final ObjectProvider<FibonacciGridPanelRenderer> fibPanel;
    private final ObjectProvider<BollingerPanelRenderer> bollPanel;

    private final ObjectProvider<TelegramBot> botProvider;
    private final MenuSessionService sessionService;

    @Value("${ui.autorefresh.ms:1000}")
    private long uiAutorefreshMs;

    @Value("${trading.autostart:false}")
    private boolean tradingAutostart;

    private ScheduledThreadPoolExecutor scheduler;
    private final Map<String, ScheduledFuture<?>> runningTasks = new ConcurrentHashMap<>();
    private ScheduledFuture<?> uiRefreshFuture;

    private final Set<String> uiAutorefreshDisabled = ConcurrentHashMap.newKeySet();
    private final Map<String, String> lastUiPayload = new ConcurrentHashMap<>();

    private static final AtomicLong SCHEDULER_THREAD_SEQ = new AtomicLong();


    /** Определение таймфрейма по имени стратегии. */
    private final Map<String, Function<Long, String>> timeframeResolvers = new HashMap<>();

    /** Поставщики chatId’ов активных стратегий для автозапуска. */
    private final Map<String, Supplier<Stream<Long>>> autostartSuppliers = new HashMap<>();

    /**
     * Определение UI-панели (рендерера) и имени состояния меню для автообновления.
     */
        private record UiMeta(String stateName, Supplier<Optional<? extends PanelRendererAdapter>> renderer) {
    }
    /** Небольшой адаптер, чтобы не зависеть от конкретных интерфейсов рендереров. */
    public interface PanelRendererAdapter {
        SendMessage render(Long chatId);
    }

    private final Map<String, UiMeta> uiByStrategy = new HashMap<>();

    @PostConstruct
    private void init() {
        int threads = Math.max(2, Runtime.getRuntime().availableProcessors());
        this.scheduler = new ScheduledThreadPoolExecutor(threads, r -> {
            Thread t = new Thread(r);
            t.setName("ai-scheduler-" + SCHEDULER_THREAD_SEQ.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        this.scheduler.setRemoveOnCancelPolicy(true);
        log.info("Планировщик инициализирован: {} поток(а/ов)", threads);

        timeframeResolvers.put("SCALPING", id ->
                scalpingRepo.findById(id).orElseThrow(() ->
                        new IllegalStateException("Scalping settings not found for chatId=" + id)).getTimeframe()
        );
        timeframeResolvers.put("FIBONACCI_GRID", id ->
                fibRepo.findById(id).orElseThrow(() ->
                        new IllegalStateException("FibonacciGrid settings not found for chatId=" + id)).getTimeframe()
        );
        timeframeResolvers.put("BOLLINGER_BANDS", id ->
                bollRepo.findById(id).orElseThrow(() ->
                        new IllegalStateException("Bollinger settings not found for chatId=" + id)).getTimeframe()
        );

        autostartSuppliers.put("SCALPING", () ->
                scalpingRepo.findAll().stream().filter(ScalpingStrategySettings::isActive).map(ScalpingStrategySettings::getChatId));
        autostartSuppliers.put("FIBONACCI_GRID", () ->
                fibRepo.findAll().stream().filter(FibonacciGridStrategySettings::isActive).map(FibonacciGridStrategySettings::getChatId));
        autostartSuppliers.put("BOLLINGER_BANDS", () ->
                bollRepo.findAll().stream().filter(BollingerStrategySettings::isActive).map(BollingerStrategySettings::getChatId));

        // ---- UI-автообновление для всех поддержанных стратегий ----
        uiByStrategy.put("SCALPING",
                new UiMeta(ScalpingConfigState.NAME,
                        () -> scalpingPanel.stream().findFirst().map(p -> p::render)));

        uiByStrategy.put("FIBONACCI_GRID",
                new UiMeta(FibonacciGridConfigState.NAME,
                        () -> fibPanel.stream().findFirst().map(p -> p::render)));

        uiByStrategy.put("BOLLINGER_BANDS",
                new UiMeta(BollingerConfigState.NAME,
                        () -> bollPanel.stream().findFirst().map(p -> p::render)));

        startUiAutorefreshIfNeeded();
        startActiveFromDbIfEnabled();
    }

    @PreDestroy
    private void shutdown() {
        log.info("Останавливаю планировщик…");
        if (uiRefreshFuture != null) uiRefreshFuture.cancel(true);
        scheduler.shutdownNow();
        lastUiPayload.clear();
    }

    // ==================== API ====================

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

        long intervalSec = Math.max(1, resolveIntervalSec(chatId, strategyName));
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

        long intervalSec = Math.max(1, resolveIntervalSec(chatId, strategyName));
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

    public void setUiAutorefreshEnabled(Long chatId, String strategyName, boolean enabled) {
        String key = buildKey(chatId, strategyName);
        if (enabled) {
            uiAutorefreshDisabled.remove(key);
            log.debug("UI автorefresh включён для {}", key);
        } else {
            uiAutorefreshDisabled.add(key);
            log.debug("UI автorefresh отключён для {}", key);
        }
    }

    // ==================== внутренности ====================

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
        Function<Long, String> resolver = timeframeResolvers.get(strategyName);
        if (resolver == null) {
            throw new IllegalArgumentException("Unknown strategy: " + strategyName);
        }
        String tf = resolver.apply(chatId);
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

    // ===== UI autorefresh для всех стратегий, у которых это уместно =====

    private void startUiAutorefreshIfNeeded() {
        if (uiAutorefreshMs <= 0) {
            log.info("UI автообновление отключено (ui.autorefresh.ms={})", uiAutorefreshMs);
            return;
        }
        if (uiRefreshFuture == null || uiRefreshFuture.isCancelled() || uiRefreshFuture.isDone()) {
            uiRefreshFuture = scheduler.scheduleAtFixedRate(
                    this::refreshPanelsSafe,
                    uiAutorefreshMs,
                    uiAutorefreshMs,
                    TimeUnit.MILLISECONDS
            );
            log.info("UI автообновление включено каждые {} мс", uiAutorefreshMs);
        }
    }

    private void refreshPanelsSafe() {
        try {
            refreshPanels();
        } catch (Exception e) {
            log.debug("UI autorefresh tick failed: {}", e.getMessage());
        }
    }

    private void refreshPanels() {
        if (runningTasks.isEmpty()) return;

        Set<String> keys = Set.copyOf(runningTasks.keySet());
        for (String key : keys) {
            // key = "<chatId>:<STRATEGY_NAME>"
            int idx = key.indexOf(':');
            if (idx <= 0) continue;

            String strategyName = key.substring(idx + 1);
            UiMeta meta = uiByStrategy.get(strategyName);
            if (meta == null) continue;

            if (uiAutorefreshDisabled.contains(key)) continue;

            Long chatId = extractChatId(key);
            if (chatId == null) continue;

            String currentState = tryGetCurrentState(chatId);
            if (!Objects.equals(meta.stateName, currentState)) continue;

            Integer messageId = tryGetLastMessageId(chatId);
            if (messageId == null) continue;

            Optional<? extends PanelRendererAdapter> rendererOpt = meta.renderer.get();
            if (rendererOpt.isEmpty()) continue;

            try {
                SendMessage sm = rendererOpt.get().render(chatId);

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
                    safeEdit(bot, edit);
                }
            } catch (Exception e) {
                log.debug("Не удалось обновить UI для chatId={}: {}", chatId, e.getMessage());
            }
        }
    }

    /** Отправляет edit только если контент реально изменился. Гасит «message is not modified». */
    private void safeEdit(TelegramBot bot, EditMessageText edit) {
        String chatId = edit.getChatId();
        Integer messageId = edit.getMessageId();
        String key = chatId + ":" + messageId;

        String payload = buildPayload(
                edit.getText(),
                edit.getReplyMarkup(),
                edit.getParseMode(),
                edit.getDisableWebPagePreview()
        );

        String prev = lastUiPayload.put(key, payload);
        if (payload.equals(prev)) {
            return;
        }
        try {
            bot.execute(edit);
        } catch (TelegramApiRequestException e) {
            String resp = e.getApiResponse();
            if (resp != null && resp.contains("message is not modified")) {
                log.debug("UI: пропущено обновление (без изменений) chatId={}, msgId={}", chatId, messageId);
                return;
            }
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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

    private void startActiveFromDbIfEnabled() {
        if (!tradingAutostart) {
            log.info("Автозапуск стратегий отключён (trading.autostart=false). Ничего не запускаем.");
            return;
        }
        autostartSuppliers.forEach((name, supplier) -> {
            try {
                supplier.get().forEach(chatId -> safeStart(chatId, name));
            } catch (Exception e) {
                log.error("Автозапуск {}: ошибка выборки активных — {}", name, e.getMessage());
            }
        });
    }

    private void safeStart(Long chatId, String name) {
        try {
            startStrategy(chatId, name);
        } catch (Exception e) {
            log.error("Автозапуск {} @{} провален: {}", name, chatId, e.getMessage());
        }
    }
}
