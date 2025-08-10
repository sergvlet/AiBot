package com.chicu.aibot.trading;

import com.chicu.aibot.strategy.scalping.model.ScalpingStrategySettings;
import com.chicu.aibot.strategy.scalping.repository.ScalpingStrategySettingsRepository;
import com.chicu.aibot.strategy.fibonacci.model.FibonacciGridStrategySettings;
import com.chicu.aibot.strategy.fibonacci.repository.FibonacciGridStrategySettingsRepository;
import com.chicu.aibot.trading.scheduler.SchedulerService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class StrategyMonitorService {

    private final ScalpingStrategySettingsRepository scalpingRepo;
    private final FibonacciGridStrategySettingsRepository fibRepo;
    private final SchedulerService schedulerService;

    private final ScheduledExecutorService monitorExecutor =
        Executors.newSingleThreadScheduledExecutor();
    private final Set<String> runningKeys = ConcurrentHashMap.newKeySet();

    @PostConstruct
    private void init() {
        // синхронизировать сразу, затем каждые 30 секунд
        monitorExecutor.scheduleAtFixedRate(
            this::syncStrategies, 0, 30, TimeUnit.SECONDS
        );
    }

    private void syncStrategies() {
        try {
            Set<String> desired = new HashSet<>();

            // Scalping
            for (ScalpingStrategySettings s : scalpingRepo.findAll()) {
                if (!s.isActive()) continue;
                String key = makeKey(s.getChatId(), "SCALPING");
                desired.add(key);
                if (!runningKeys.contains(key)) {
                    schedulerService.startStrategy(s.getChatId(), "SCALPING");
                    runningKeys.add(key);
                }
            }

            // Fibonacci Grid
            for (FibonacciGridStrategySettings f : fibRepo.findAll()) {
                if (!f.isActive()) continue;
                String key = makeKey(f.getChatId(), "FIBONACCI_GRID");
                desired.add(key);
                if (!runningKeys.contains(key)) {
                    schedulerService.startStrategy(f.getChatId(), "FIBONACCI_GRID");
                    runningKeys.add(key);
                }
            }

            for (Iterator<String> it = runningKeys.iterator(); it.hasNext();) {
                String key = it.next();
                if (!desired.contains(key)) {
                    String[] parts = key.split(":");
                    Long chatId = Long.valueOf(parts[0]);
                    String strat = parts[1];
                    schedulerService.stopStrategy(chatId, strat);
                    it.remove();
                }
            }
        } catch (Exception e) {
            log.error("Error syncing strategies", e);
        }
    }

    private String makeKey(Long chatId, String strat) {
        return chatId + ":" + strat;
    }
}
