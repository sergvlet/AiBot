package com.chicu.aibot.trading.events;

import com.chicu.aibot.strategy.StrategyType;
import com.chicu.aibot.strategy.fibonacci.repository.FibonacciGridStrategySettingsRepository;
import com.chicu.aibot.strategy.scalping.repository.ScalpingStrategySettingsRepository;
import com.chicu.aibot.trading.scheduler.SchedulerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Slf4j
@Component
@RequiredArgsConstructor
public class StrategySettingsChangedListener {

    private final SchedulerService scheduler;
    private final ScalpingStrategySettingsRepository scalpingRepo;
    private final FibonacciGridStrategySettingsRepository fibRepo;

    /** Реагируем только после коммитов транзакции сохранения настроек. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChanged(StrategySettingsChangedEvent e) {
        Long chatId = e.chatId();
        StrategyType type = e.strategyType();

        boolean desiredActive = switch (type) {
            case SCALPING -> scalpingRepo.findById(chatId)
                    .map(this::extractActiveSafe)
                    .orElse(false);
            case FIBONACCI_GRID -> fibRepo.findById(chatId)
                    .map(this::extractActiveSafe)
                    .orElse(false);
            default -> {
                log.warn("Получено событие для неизвестной стратегии {} @{}", type, chatId);
                yield false;
            }
        };

        boolean running = scheduler.isStrategyActive(chatId, type.name());

        if (desiredActive && running) {
            log.info("Настройки {} @{} изменились — перезапускаю", type, chatId);
            scheduler.restartStrategy(chatId, type.name());
        } else if (desiredActive) {
            log.info("Настройки {} @{} активны — запускаю", type, chatId);
            scheduler.startStrategy(chatId, type.name());
        } else if (running) {
            log.info("Настройки {} @{} деактивированы — останавливаю", type, chatId);
            scheduler.stopStrategy(chatId, type.name());
        } else {
            log.info("Настройки {} @{} деактивированы, задача и так не запущена — игнорирую", type, chatId);
        }
    }

    /** Аккуратно достаём флаг active из модели, поддерживая getActive() и isActive(). */
    private boolean extractActiveSafe(Object settings) {
        if (settings == null) return false;
        try {
            var m = settings.getClass().getMethod("getActive");
            Object v = m.invoke(settings);
            return Boolean.TRUE.equals(v);
        } catch (NoSuchMethodException noGet) {
            try {
                var m = settings.getClass().getMethod("isActive");
                Object v = m.invoke(settings);
                return v instanceof Boolean b && b;
            } catch (NoSuchMethodException noIs) {
                log.debug("Модель {} не содержит getActive()/isActive()", settings.getClass().getSimpleName());
                return false;
            } catch (Exception ex2) {
                log.warn("Ошибка чтения isActive(): {}", ex2.getMessage());
                return false;
            }
        } catch (Exception ex) {
            log.warn("Ошибка чтения getActive(): {}", ex.getMessage());
            return false;
        }
    }
}
