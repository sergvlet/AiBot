package com.chicu.aibot.bot.menu.feature.ai.strategy.fibonacci;

import com.chicu.aibot.bot.menu.core.MenuSessionService;
import com.chicu.aibot.bot.menu.core.MenuState;
import com.chicu.aibot.bot.menu.feature.ai.strategy.fibonacci.service.FibonacciGridPanelRenderer;
import com.chicu.aibot.bot.menu.feature.ai.strategy.fibonacci.service.impl.FibonacciGridPanelRendererImpl;
import com.chicu.aibot.bot.menu.feature.common.AiSelectSymbolState;
import com.chicu.aibot.bot.ui.UiAutorefreshService;
import com.chicu.aibot.exchange.order.model.ExchangeOrderEntity;
import com.chicu.aibot.exchange.order.repository.ExchangeOrderRepository;
import com.chicu.aibot.strategy.fibonacci.model.FibonacciGridStrategySettings;
import com.chicu.aibot.strategy.fibonacci.service.FibonacciGridStrategySettingsService;
import com.chicu.aibot.strategy.model.Order;
import com.chicu.aibot.strategy.service.OrderService;
import com.chicu.aibot.trading.scheduler.SchedulerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class FibonacciGridConfigState implements MenuState {

    public static final String NAME = "ai_trading_fibonacci_config";

    private final FibonacciGridStrategySettingsService settingsService;
    private final MenuSessionService sessionService;
    private final SchedulerService schedulerService;
    private final FibonacciGridPanelRenderer panelRenderer;
    private final UiAutorefreshService uiAutorefresh;

    // Отмена ордеров не покидая экран
    private final OrderService orderService;

    // ✅ используем репозиторий — у него точно есть save(...) и выборки по статусу
    private final ExchangeOrderRepository orderRepo;

    @Override
    public String name() { return NAME; }

    @Override
    public SendMessage render(Long chatId) {
        uiAutorefresh.enable(chatId, NAME);
        return panelRenderer.render(chatId);
    }

    @Override
    public String handleInput(Update update) {
        if (!update.hasCallbackQuery()) return NAME;

        final String data = update.getCallbackQuery().getData();
        final Long chatId = update.getCallbackQuery().getMessage().getChatId();
        if (data == null) return NAME;

        if (FibonacciGridPanelRendererImpl.BTN_REFRESH.equals(data)) {
            return NAME;
        }

        if (FibonacciGridPanelRendererImpl.BTN_TOGGLE_ACTIVE.equals(data)) {
            FibonacciGridStrategySettings s = settingsService.getOrCreate(chatId);
            s.setActive(!s.isActive());
            if (s.isActive()) {
                schedulerService.startStrategy(chatId, s.getType().name());
            } else {
                schedulerService.stopStrategy(chatId, s.getType().name());
            }
            settingsService.save(s);
            return NAME;
        }

        if ("ai_trading".equals(data)) {
            uiAutorefresh.disable(chatId, NAME);
            return "ai_trading";
        }

        if (FibonacciGridPanelRendererImpl.BTN_EDIT_SYMBOL.equals(data)) {
            uiAutorefresh.disable(chatId, NAME);
            sessionService.setEditingField(chatId, "symbol");
            sessionService.setReturnState(chatId, NAME);
            return AiSelectSymbolState.NAME;
        }

        if (FibonacciGridPanelRendererImpl.BTN_HELP.equals(data)) {
            uiAutorefresh.disable(chatId, NAME);
            return FibonacciGridHelpState.NAME;
        }

        if (data.startsWith("fib_edit_")) {
            uiAutorefresh.disable(chatId, NAME);
            String field = data.substring("fib_edit_".length());
            sessionService.setEditingField(chatId, field);
            return FibonacciGridAdjustState.NAME;
        }

        // --- Отмена ордеров прямо из этого экрана ---

        if (data.startsWith(FibonacciGridPanelRendererImpl.BTN_CANCEL_ORDER_PREFIX)) {
            String orderId = data.substring(FibonacciGridPanelRendererImpl.BTN_CANCEL_ORDER_PREFIX.length());
            cancelSingleOrderSafe(chatId, orderId);
            return NAME;
        }

        if (FibonacciGridPanelRendererImpl.BTN_CANCEL_ALL.equals(data)) {
            cancelAllOrdersSafe(chatId);
            return NAME;
        }

        return NAME;
    }

    private void cancelSingleOrderSafe(Long chatId, String orderId) {
        try {
            FibonacciGridStrategySettings s = settingsService.getOrCreate(chatId);
            String symbol = s.getSymbol();
            if (symbol == null || symbol.isBlank()) return;

            // 1) отменяем на бирже
            List<Order> active = orderService.loadActiveOrders(chatId, symbol);
            active.stream()
                  .filter(o -> Objects.equals(o.getId(), orderId))
                  .findFirst()
                  .ifPresent(o -> orderService.cancel(chatId, o));

            // 2) ⚡ оптимистично скрываем из UI
            optimisticMarkCanceled(chatId, symbol, orderId);

            log.info("[FibCFG] Отменён ордер {} по {}", orderId, symbol);
        } catch (Exception e) {
            log.warn("[FibCFG] Ошибка отмены ордера {}: {}", orderId, e.getMessage());
        }
    }

    private void cancelAllOrdersSafe(Long chatId) {
        try {
            FibonacciGridStrategySettings s = settingsService.getOrCreate(chatId);
            String symbol = s.getSymbol();
            if (symbol == null || symbol.isBlank()) return;

            // Список активных до отмены, чтобы знать какие id помечать
            List<Order> activeBefore = orderService.loadActiveOrders(chatId, symbol);
            Set<String> canceledIds = new HashSet<>();

            for (Order o : activeBefore) {
                try {
                    orderService.cancel(chatId, o);
                    canceledIds.add(o.getId());
                } catch (Exception ex) {
                    log.warn("[FibCFG] Ошибка отмены ордера {}: {}", o.getId(), ex.getMessage());
                }
            }

            // ⚡ оптимистично скрываем из UI именно те, что пытались отменить
            for (String oid : canceledIds) {
                optimisticMarkCanceled(chatId, symbol, oid);
            }

            log.info("[FibCFG] Отменены все активные ордера по {}", symbol);
        } catch (Exception e) {
            log.warn("[FibCFG] Ошибка массовой отмены ордеров: {}", e.getMessage());
        }
    }

    /** Помечаем в БД как CANCELED ту запись, что совпадает по chatId/symbol и orderId, среди статуса NEW. */
    private void optimisticMarkCanceled(Long chatId, String symbol, String orderId) {
        try {
            List<ExchangeOrderEntity> open = orderRepo.findByChatIdAndSymbolAndStatus(chatId, symbol, "NEW");
            if (open == null || open.isEmpty()) return;

            for (ExchangeOrderEntity e : open) {
                if (Objects.equals(orderId, e.getOrderId())) {
                    e.setStatus("CANCELED");
                    e.setUpdatedAt(Instant.now());
                    orderRepo.save(e);
                    break;
                }
            }
        } catch (Exception ex) {
            // не критично — UI всё равно обновится, когда подтянется реальное состояние
            log.debug("[FibCFG] optimisticMarkCanceled skip ({}:{}:{}): {}", chatId, symbol, orderId, ex.getMessage());
        }
    }
}
