package com.chicu.aibot.strategy.service.impl;

import com.chicu.aibot.exchange.client.ExchangeClient;
import com.chicu.aibot.exchange.enums.OrderSide;
import com.chicu.aibot.exchange.order.model.ExchangeOrderEntity;
import com.chicu.aibot.exchange.order.repository.ExchangeOrderRepository;
import com.chicu.aibot.strategy.service.HousekeepingResult;
import com.chicu.aibot.strategy.service.OrderHousekeeperService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderHousekeeperServiceImpl implements OrderHousekeeperService {

    /** Все доступные клиенты бирж, ключи = имена бинов: "BINANCE", "BYBIT", ... */
    private final Map<String, ExchangeClient> exchangeClients;

    private final ExchangeOrderRepository orderRepo;

    /**
     * - Дедуп по ключу (side + нормализованный price-строкой).
     * - Оставляем самый свежий (updatedAt максимальный), остальные отменяем на бирже (если есть id) и удаляем из БД.
     * - Ограничиваем число активных ордеров на сторону (maxActivePerSide): ближние к рынку оставляем.
     * - Закрытые (FILLED/CANCELED/...) не трогаем (мы работаем только со статусом NEW).
     */
    @Override
    @Transactional
    public HousekeepingResult reconcile(Long chatId, String symbol, int maxActivePerSide) {
        // Берём только "NEW"
        List<ExchangeOrderEntity> dbOpen = orderRepo.findByChatIdAndSymbolAndStatus(chatId, symbol, "NEW");

        // 1) Дедупликация по (side + priceKey)
        Map<String, List<ExchangeOrderEntity>> buckets = new HashMap<>();
        for (ExchangeOrderEntity o : dbOpen) {
            String side = o.getSide() == null ? "?" : o.getSide();
            String priceKey = (o.getPrice() == null) ? "0" : toPriceKey(o.getPrice());
            String key = side + "@" + priceKey;
            buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(o);
        }

        int cancelledDup = 0;
        for (var entry : buckets.entrySet()) {
            List<ExchangeOrderEntity> list = entry.getValue();
            if (list.size() <= 1) continue;

            // Самый свежий — updatedAt максимальный (null считаем самым старым)
            list.sort(Comparator.comparing(ExchangeOrderEntity::getUpdatedAt,
                    Comparator.nullsFirst(Comparator.naturalOrder())));

            // Оставляем последний, остальные отменяем/удаляем
            for (int i = 0; i < list.size() - 1; i++) {
                ExchangeOrderEntity dup = list.get(i);
                if (safeCancelOnExchangeThenDelete(dup)) {
                    cancelledDup++;
                }
            }
        }

        // 2) Ограничиваем per-side
        List<ExchangeOrderEntity> nowOpen = orderRepo.findByChatIdAndSymbolAndStatus(chatId, symbol, "NEW");

        Map<OrderSide, List<ExchangeOrderEntity>> bySide = nowOpen.stream()
                .collect(Collectors.groupingBy(e -> {
                    try {
                        return OrderSide.valueOf(safeStr(e.getSide()).toUpperCase());
                    } catch (Exception ignore) { // на случай мусорного значения
                        return OrderSide.BUY;
                    }
                }));

        int cancelledExcess = 0;
        cancelledExcess += trimSide(bySide.get(OrderSide.BUY),  OrderSide.BUY,  maxActivePerSide);
        cancelledExcess += trimSide(bySide.get(OrderSide.SELL), OrderSide.SELL, maxActivePerSide);

        int leftBuy  = (int) orderRepo.countByChatIdAndSymbolAndStatusAndSide(chatId, symbol, "NEW", OrderSide.BUY);
        int leftSell = (int) orderRepo.countByChatIdAndSymbolAndStatusAndSide(chatId, symbol, "NEW", OrderSide.SELL);

        HousekeepingResult res = HousekeepingResult.builder()
                .buyActive(leftBuy)
                .sellActive(leftSell)
                .removedDb(0) // здесь «мёртвые» по сверке с биржей не чистим
                .cancelled(cancelledDup + cancelledExcess)
                .build();

        if (res.getRemovedDb() > 0 || res.getCancelled() > 0) {
            log.info("Housekeeper[{}:{}]: removedDb={}, cancelled={}, left BUY={}, SELL={}",
                    chatId, symbol, res.getRemovedDb(), res.getCancelled(), res.getBuyActive(), res.getSellActive());
        }
        return res;
    }

    /** Ограничение числа активных ордеров на сторону: оставляем ближние к рынку. */
    private int trimSide(List<ExchangeOrderEntity> sideList, OrderSide side, int maxActivePerSide) {
        if (sideList == null || sideList.size() <= maxActivePerSide) return 0;

        // Для BUY — выше цена приоритетнее, для SELL — ниже.
        sideList.sort((a, b) -> {
            BigDecimal pa = a.getPrice() == null ? BigDecimal.ZERO : a.getPrice();
            BigDecimal pb = b.getPrice() == null ? BigDecimal.ZERO : b.getPrice();
            int cmp = pa.compareTo(pb);
            return (side == OrderSide.BUY) ? -cmp : cmp;
        });

        int cancelled = 0;
        for (int i = maxActivePerSide; i < sideList.size(); i++) {
            ExchangeOrderEntity excess = sideList.get(i);
            if (safeCancelOnExchangeThenDelete(excess)) {
                cancelled++;
            }
        }
        return cancelled;
    }

    /**
     * Отмена на бирже для конкретной записи.
     * Берём exchange из сущности, подбираем нужный клиент.
     * Используем orderId, а если пуст — clientOrderId (origClientOrderId / orderLinkId).
     * Если оба пустые — удаляем только из БД.
     */
    private boolean safeCancelOnExchangeThenDelete(ExchangeOrderEntity o) {
        String orderId       = safeStr(o.getOrderId());
        String clientOrderId = getClientOrderIdSoft(o);

        if (orderId.isBlank() && clientOrderId.isBlank()) {
            orderRepo.delete(o);
            log.info("HK cancel skip (no ids) → deleted only from DB: dbId={}", o.getId());
            return true;
        }

        String exchangeName = safeStr(o.getExchange()).trim();
        ExchangeClient client = resolveClient(exchangeName);

        try {
            client.cancelOrder(
                    exchangeName,                 // имя/код биржи (как требует твой интерфейс)
                    safeStr(o.getSymbol()),       // символ
                    o.getNetwork(),               // сеть
                    orderId.isBlank() ? null : orderId,
                    clientOrderId.isBlank() ? null : clientOrderId
            );
            orderRepo.delete(o);
            log.info("HK cancelled on exchange and removed from DB: exchange={}, dbId={}, orderId={}, clientOrderId={}",
                    exchangeName, o.getId(), orderId, clientOrderId);
            return true;
        } catch (Exception e) {
            log.warn("HK cancel failed: exchange={}, dbId={}, orderId={}, clientOrderId={}, reason={}",
                    exchangeName, o.getId(), orderId, clientOrderId, e.getMessage());
            // Пометим updatedAt, чтобы не «залипало» в дедупе этой же итерации
            o.setUpdatedAt(Instant.now());
            orderRepo.save(o);
            return false;
        }
    }

    // ---------- utils ----------

    private ExchangeClient resolveClient(String exchangeName) {
        if (exchangeName != null) {
            ExchangeClient c = exchangeClients.get(exchangeName);
            if (c == null && !exchangeClients.isEmpty()) {
                // Попробуем по верхнему регистру (на случай "binance" -> "BINANCE")
                c = exchangeClients.get(exchangeName.toUpperCase(Locale.ROOT));
            }
            if (c != null) return c;
        }
        // Фолбэк: если бин один — берём его; иначе кидаем понятную ошибку
        if (exchangeClients.size() == 1) {
            return exchangeClients.values().iterator().next();
        }
        throw new IllegalStateException("No ExchangeClient bean matched for exchange='" + exchangeName
                                        + "'. Available: " + exchangeClients.keySet());
    }

    /** Нормализуем цену в строку-ключ (без лишних нулей). */
    private static String toPriceKey(BigDecimal p) {
        return p.stripTrailingZeros().toPlainString();
    }

    private static String safeStr(Object v) {
        return v == null ? "" : v.toString();
    }

    /** Мягкое получение clientOrderId из сущности любым известным геттером. */
    private static String getClientOrderIdSoft(ExchangeOrderEntity e) {
        String v = firstNonBlank(
                tryInvokeStr(e, "getClientOrderId"),
                tryInvokeStr(e, "getOrigClientOrderId"),
                tryInvokeStr(e, "getOrderLinkId"),
                tryInvokeStr(e, "getClientOrderID"),
                tryInvokeStr(e, "getOrigClientOrderID")
        );
        return v;
    }

    private static String tryInvokeStr(Object target, String getter) {
        try {
            Method m = target.getClass().getMethod(getter);
            Object val = m.invoke(target);
            return val == null ? "" : val.toString();
        } catch (Exception ignore) {
            return "";
        }
    }

    private static String firstNonBlank(String... vals) {
        if (vals == null) return "";
        for (String s : vals) {
            if (s != null && !s.isBlank()) return s;
        }
        return "";
    }
}
