package com.chicu.aibot.trading.scheduler.impl;

import com.chicu.aibot.bot.TelegramBot;
import com.chicu.aibot.bot.menu.core.MenuSessionService;
import com.chicu.aibot.bot.menu.feature.ai.strategy.bollinger.BollingerConfigState;
import com.chicu.aibot.bot.menu.feature.ai.strategy.bollinger.service.BollingerPanelRenderer;
import com.chicu.aibot.bot.menu.feature.ai.strategy.fibonacci.FibonacciGridConfigState;
import com.chicu.aibot.bot.menu.feature.ai.strategy.fibonacci.service.FibonacciGridPanelRenderer;
import com.chicu.aibot.bot.menu.feature.ai.strategy.ml_invest.AiTradingMlInvestConfigState;
import com.chicu.aibot.bot.menu.feature.ai.strategy.ml_invest.service.MlInvestPanelRenderer;
import com.chicu.aibot.bot.menu.feature.ai.strategy.scalping.ScalpingConfigState;
import com.chicu.aibot.bot.menu.feature.ai.strategy.scalping.service.ScalpingPanelRenderer;
import com.chicu.aibot.strategy.StrategyRegistry;
import com.chicu.aibot.strategy.TradingStrategy;
import com.chicu.aibot.strategy.bollinger.model.BollingerStrategySettings;
import com.chicu.aibot.strategy.bollinger.repository.BollingerStrategySettingsRepository;
import com.chicu.aibot.strategy.fibonacci.model.FibonacciGridStrategySettings;
import com.chicu.aibot.strategy.fibonacci.repository.FibonacciGridStrategySettingsRepository;
import com.chicu.aibot.strategy.ml_invest.model.MachineLearningInvestStrategySettings;
import com.chicu.aibot.strategy.ml_invest.repository.MachineLearningInvestStrategySettingsRepository;
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

    // репозитории
    private final ScalpingStrategySettingsRepository scalpingRepo;
    private final FibonacciGridStrategySettingsRepository fibRepo;
    private final BollingerStrategySettingsRepository bollRepo;
    private final MachineLearningInvestStrategySettingsRepository mlRepo;

    // панели
    private final ObjectProvider<ScalpingPanelRenderer> scalpingPanel;
    private final ObjectProvider<FibonacciGridPanelRenderer> fibPanel;
    private final ObjectProvider<BollingerPanelRenderer> bollPanel;
    private final ObjectProvider<MlInvestPanelRenderer> mlPanel;

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

    private final Map<String, Function<Long, String>> timeframeResolvers = new HashMap<>();
    private final Map<String, Supplier<Stream<Long>>> autostartSuppliers = new HashMap<>();

    private record UiMeta(String stateName, Supplier<Optional<? extends PanelRendererAdapter>> renderer) {}
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

        // ===== резолверы таймфрейма =====
        timeframeResolvers.put("SCALPING", id ->
                scalpingRepo.findByChatId(id)
                        .orElseThrow(() -> new IllegalStateException("Scalping settings not found for chatId=" + id))
                        .getTimeframe()
        );
        timeframeResolvers.put("FIBONACCI_GRID", id ->
                fibRepo.findByChatId(id)
                        .orElseThrow(() -> new IllegalStateException("FibonacciGrid settings not found for chatId=" + id))
                        .getTimeframe()
        );
        timeframeResolvers.put("BOLLINGER_BANDS", id ->
                bollRepo.findByChatId(id)
                        .orElseThrow(() -> new IllegalStateException("Bollinger settings not found for chatId=" + id))
                        .getTimeframe()
        );
        timeframeResolvers.put("MACHINE_LEARNING_INVEST", id -> {
            var settings = mlRepo.findByChatId(id).orElseGet(() -> {
                MachineLearningInvestStrategySettings s = MachineLearningInvestStrategySettings.builder()
                        .chatId(id)
                        .timeframe("1m")
                        .active(false)
                        .build();
                mlRepo.save(s);
                log.warn("[ML-Invest] Автоматически созданы настройки для chatId={}", id);
                return s;
            });
            return settings.getTimeframe();
        });

        // ===== автозапуск =====
        autostartSuppliers.put("SCALPING", () ->
                scalpingRepo.findAll().stream().filter(ScalpingStrategySettings::isActive).map(ScalpingStrategySettings::getChatId));
        autostartSuppliers.put("FIBONACCI_GRID", () ->
                fibRepo.findAll().stream().filter(FibonacciGridStrategySettings::isActive).map(FibonacciGridStrategySettings::getChatId));
        autostartSuppliers.put("BOLLINGER_BANDS", () ->
                bollRepo.findAll().stream().filter(BollingerStrategySettings::isActive).map(BollingerStrategySettings::getChatId));
        autostartSuppliers.put("MACHINE_LEARNING_INVEST", () ->
                mlRepo.findAll().stream().filter(MachineLearningInvestStrategySettings::isActive).map(MachineLearningInvestStrategySettings::getChatId));

        // ===== UI =====
        uiByStrategy.put("SCALPING",
                new UiMeta(ScalpingConfigState.NAME,
                        () -> scalpingPanel.stream().findFirst().map(p -> p::render)));
        uiByStrategy.put("FIBONACCI_GRID",
                new UiMeta(FibonacciGridConfigState.NAME,
                        () -> fibPanel.stream().findFirst().map(p -> p::render)));
        uiByStrategy.put("BOLLINGER_BANDS",
                new UiMeta(BollingerConfigState.NAME,
                        () -> bollPanel.stream().findFirst().map(p -> p::render)));
        uiByStrategy.put("MACHINE_LEARNING_INVEST",
                new UiMeta(AiTradingMlInvestConfigState.NAME,
                        () -> mlPanel.stream().findFirst().map(p -> p::render)));

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

    @Override
    public void startStrategy(Long chatId, String strategyName) {
        String key = buildKey(chatId, strategyName);

        ScheduledFuture<?> existing = runningTasks.get(key);
        if (existing != null && !existing.isCancelled() && !existing.isDone()) {
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
            log.info("Стратегия {} не запущена для chatId={}", strategyName, chatId);
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

    @Override
    public boolean isStrategyActive(Long chatId, String strategyName) {
        String key = buildKey(chatId, strategyName);
        ScheduledFuture<?> f = runningTasks.get(key);
        return f != null && !f.isDone() && !f.isCancelled();
    }

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
        if (resolver == null) throw new IllegalArgumentException("Unknown strategy: " + strategyName);
        String tf = resolver.apply(chatId);
        return parseTimeframe(tf);
    }

    private long parseTimeframe(String tfRaw) {
        if (tfRaw == null || tfRaw.isBlank()) return 60;
        String tf = tfRaw.trim().toLowerCase();
        char unit = tf.charAt(tf.length() - 1);
        String num = tf.substring(0, tf.length() - 1);
        long value = Long.parseLong(num);
        return switch (unit) {
            case 's' -> Duration.ofSeconds(value).getSeconds();
            case 'm' -> Duration.ofMinutes(value).getSeconds();
            case 'h' -> Duration.ofHours(value).getSeconds();
            case 'd' -> Duration.ofDays(value).getSeconds();
            default -> 60;
        };
    }

    private String buildKey(Long chatId, String strategyName) {
        return chatId + ":" + strategyName;
    }

    // ===== UI AUTOREFRESH =====
    private void startUiAutorefreshIfNeeded() {
        if (uiAutorefreshMs <= 0) {
            log.info("UI автообновление отключено");
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
            log.debug("UI tick error: {}", e.getMessage());
        }
    }

    private void refreshPanels() {
        if (runningTasks.isEmpty()) return;

        for (String key : runningTasks.keySet()) {
            int idx = key.indexOf(':');
            if (idx <= 0) continue;

            String strategyName = key.substring(idx + 1);
            UiMeta meta = uiByStrategy.get(strategyName);
            if (meta == null) continue;

            Long chatId = extractChatId(key);
            if (chatId == null) continue;

            String currentState = tryGetCurrentState(chatId);
            if (!Objects.equals(meta.stateName, currentState)) continue;

            Integer msgId = tryGetLastMessageId(chatId);
            if (msgId == null) continue;

            meta.renderer.get().ifPresent(r -> {
                try {
                    SendMessage sm = r.render(chatId);
                    EditMessageText edit = EditMessageText.builder()
                            .chatId(chatId.toString())
                            .messageId(msgId)
                            .text(sm.getText())
                            .parseMode(sm.getParseMode())
                            .replyMarkup((InlineKeyboardMarkup) sm.getReplyMarkup())
                            .build();

                    TelegramBot bot = botProvider.getIfAvailable();
                    if (bot != null) safeEdit(bot, edit);
                } catch (Exception e) {
                    log.debug("Не удалось обновить UI chatId={}: {}", chatId, e.getMessage());
                }
            });
        }
    }

    private void safeEdit(TelegramBot bot, EditMessageText edit) {
        try {
            bot.execute(edit);
        } catch (TelegramApiRequestException e) {
            if (e.getApiResponse() != null && e.getApiResponse().contains("message is not modified")) return;
            log.debug("Ошибка Telegram при обновлении: {}", e.getMessage());
        } catch (Exception e) {
            log.debug("Ошибка safeEdit: {}", e.getMessage());
        }
    }

    private Long extractChatId(String key) {
        int idx = key.indexOf(':');
        if (idx <= 0) return null;
        try {
            return Long.parseLong(key.substring(0, idx));
        } catch (Exception e) {
            return null;
        }
    }

    private String tryGetCurrentState(Long chatId) {
        try {
            Method m = sessionService.getClass().getMethod("getCurrentState", Long.class);
            Object r = m.invoke(sessionService, chatId);
            return r == null ? null : r.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private Integer tryGetLastMessageId(Long chatId) {
        for (String name : List.of("getLastMessageId", "getLastBotMessageId", "getMessageId")) {
            try {
                Method m = sessionService.getClass().getMethod(name, Long.class);
                Object r = m.invoke(sessionService, chatId);
                if (r instanceof Integer i) return i;
                if (r instanceof Number n) return n.intValue();
            } catch (Exception ignore) {
            }
        }
        return null;
    }

    private void startActiveFromDbIfEnabled() {
        if (!tradingAutostart) {
            log.info("Автозапуск стратегий отключён (trading.autostart=false)");
            return;
        }
        autostartSuppliers.forEach((name, supplier) -> {
            try {
                supplier.get().forEach(chatId -> safeStart(chatId, name));
            } catch (Exception e) {
                log.error("Автозапуск {}: {}", name, e.getMessage());
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
