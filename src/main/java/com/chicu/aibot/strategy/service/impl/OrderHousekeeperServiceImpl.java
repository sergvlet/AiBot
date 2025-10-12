package com.chicu.aibot.strategy.service.impl;

import com.chicu.aibot.exchange.client.ExchangeClient;
import com.chicu.aibot.exchange.order.model.ExchangeOrderEntity;
import com.chicu.aibot.exchange.order.repository.ExchangeOrderRepository;
import com.chicu.aibot.strategy.service.HousekeepingResult;
import com.chicu.aibot.strategy.service.OrderHousekeeperService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderHousekeeperServiceImpl implements OrderHousekeeperService {

    // Внедряем все доступные клиенты бирж; ключи — имена бинов (в твоём случае "BINANCE", "BYBIT")
    private final Map<String, ExchangeClient> clients;
    private final ExchangeOrderRepository orderRepo;

    private static final int PRICE_BUCKET_SCALE = 8;

    @Override
    @Transactional
    public HousekeepingResult reconcile(Long chatId, String symbol, int maxActivePerSide) {
        // Активные ордера из БД
        List<ExchangeOrderEntity> dbOpen = orderRepo.findByChatIdAndSymbolAndStatus(chatId, symbol, "NEW");

        int removedDb = 0; // здесь не чистим «мёртвые на бирже»
        int cancelled = 0;

        // 1) Дедупликация по ключу (side@roundedPrice)
        Map<String, List<ExchangeOrderEntity>> buckets = new HashMap<>();
        for (ExchangeOrderEntity o : dbOpen) {
            String side = safe(o.getSide());
            BigDecimal price = roundPrice(o.getPrice());
            String key = side + "@" + (price == null ? "" : price.toPlainString());
            buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(o);
        }

        for (List<ExchangeOrderEntity> list : buckets.values()) {
            if (list.size() <= 1) continue;

            // оставляем самый свежий, остальные — отменяем
            list.sort(Comparator.comparing(OrderHousekeeperServiceImpl::lastUpdateTs));
            for (int i = 0; i < list.size() - 1; i++) {
                ExchangeOrderEntity dup = list.get(i);
                cancelled += tryCancelOnExchange(dup);
                orderRepo.delete(dup);
            }
        }

        // 2) Ограничение числа активных ордеров по стороне
        List<ExchangeOrderEntity> nowOpen = orderRepo.findByChatIdAndSymbolAndStatus(chatId, symbol, "NEW");
        Map<String, List<ExchangeOrderEntity>> bySide = nowOpen.stream()
                .collect(Collectors.groupingBy(o -> safe(o.getSide())));

        cancelled += trimSide(bySide.get("BUY"),  maxActivePerSide);
        cancelled += trimSide(bySide.get("SELL"), maxActivePerSide);

        int leftBuy  = (int) orderRepo.countByChatIdAndSymbolAndStatusAndSide(
                chatId, symbol, "NEW", com.chicu.aibot.exchange.enums.OrderSide.BUY);
        int leftSell = (int) orderRepo.countByChatIdAndSymbolAndStatusAndSide(
                chatId, symbol, "NEW", com.chicu.aibot.exchange.enums.OrderSide.SELL);

        HousekeepingResult res = HousekeepingResult.builder()
                .buyActive(leftBuy)
                .sellActive(leftSell)
                .removedDb(removedDb)
                .cancelled(cancelled)
                .build();

        if (res.getRemovedDb() > 0 || res.getCancelled() > 0) {
            log.info("Housekeeper[{}:{}]: removedDb={}, cancelled={}, left BUY={}, SELL={}",
                    chatId, symbol, res.getRemovedDb(), res.getCancelled(), res.getBuyActive(), res.getSellActive());
        }
        return res;
    }

    /** Безопасная отмена на нужной бирже. */
    private int tryCancelOnExchange(ExchangeOrderEntity o) {
        try {
            ExchangeClient client = resolveClient(o.getExchange());
            if (client == null) {
                log.warn("Cancel skipped: no ExchangeClient for exchange='{}' (orderId={})", o.getExchange(), o.getOrderId());
                return 0;
            }
            String clientOrderId = clientIdOrBlank(o);
            // Сигнатура: (exchange, symbol, network, orderId, clientOrderId)
            client.cancelOrder(
                    o.getExchange(),
                    o.getSymbol(),
                    o.getNetwork(),
                    o.getOrderId(),
                    clientOrderId
            );
            return 1;
        } catch (Exception ex) {
            log.debug("Cancel failed (ignored): exch={}, sym={}, orderId={}, reason={}",
                    o.getExchange(), o.getSymbol(), o.getOrderId(), ex.getMessage());
            // попытка была — считаем как отменённый, чтобы не застревало
            return 1;
        }
    }

    /** Разрешает клиента по имени бина/биржи, без учёта регистра. */
    private ExchangeClient resolveClient(String exchangeName) {
        if (exchangeName == null) return fallbackClient();
        ExchangeClient byExact = clients.get(exchangeName);
        if (byExact != null) return byExact;
        ExchangeClient byUpper = clients.get(exchangeName.toUpperCase(Locale.ROOT));
        if (byUpper != null) return byUpper;
        ExchangeClient byLower = clients.get(exchangeName.toLowerCase(Locale.ROOT));
        if (byLower != null) return byLower;
        return fallbackClient();
    }

    /** Если в контексте только один клиент — используем его как запасной. */
    private ExchangeClient fallbackClient() {
        if (clients.size() == 1) {
            return clients.values().iterator().next();
        }
        return null;
    }

    /** Достаём clientOrderId через известные геттеры или возвращаем пустую строку. */
    private static String clientIdOrBlank(ExchangeOrderEntity o) {
        String viaKnown = firstNonBlank(
                callStringGetter(o, "getClientOrderId"),
                callStringGetter(o, "getClientId"),
                callStringGetter(o, "getClOrdId"),
                callStringGetter(o, "getOrigClientOrderId")
        );
        return viaKnown != null ? viaKnown : "";
    }

    private static String callStringGetter(Object target, String method) {
        try {
            var m = target.getClass().getMethod(method);
            Object v = m.invoke(target);
            return v == null ? null : v.toString();
        } catch (Exception ignore) {
            return null;
        }
    }

    private static String firstNonBlank(String... vals) {
        if (vals == null) return null;
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private int trimSide(List<ExchangeOrderEntity> sideList, int max) {
        if (sideList == null || sideList.size() <= max) return 0;

        String side = sideList.get(0).getSide(); // одна сторона у списка
        if ("BUY".equalsIgnoreCase(side)) {
            sideList.sort(Comparator.comparing(ExchangeOrderEntity::getPrice, Comparator.nullsLast(Comparator.reverseOrder())));
        } else {
            sideList.sort(Comparator.comparing(ExchangeOrderEntity::getPrice, Comparator.nullsLast(Comparator.naturalOrder())));
        }

        int cancelled = 0;
        for (int i = max; i < sideList.size(); i++) {
            ExchangeOrderEntity excess = sideList.get(i);
            cancelled += tryCancelOnExchange(excess);
            orderRepo.delete(excess);
        }
        return cancelled;
    }

    private static BigDecimal roundPrice(BigDecimal p) {
        if (p == null) return null;
        int scale = Math.min(Math.max(p.scale(), 0), PRICE_BUCKET_SCALE);
        return p.setScale(scale, RoundingMode.DOWN).stripTrailingZeros();
    }

    private static Instant lastUpdateTs(ExchangeOrderEntity o) {
        if (o.getUpdatedAt() != null) return o.getUpdatedAt();
        if (o.getCreatedAt() != null) return o.getCreatedAt();
        return Instant.EPOCH;
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
