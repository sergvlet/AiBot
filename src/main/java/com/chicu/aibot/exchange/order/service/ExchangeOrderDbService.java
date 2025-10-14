package com.chicu.aibot.exchange.order.service;

import com.chicu.aibot.exchange.enums.NetworkType;
import com.chicu.aibot.exchange.model.OrderRequest;
import com.chicu.aibot.exchange.model.OrderResponse;
import com.chicu.aibot.exchange.order.model.ExchangeOrderEntity;
import com.chicu.aibot.exchange.order.repository.ExchangeOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ExchangeOrderDbService {

    private final ExchangeOrderRepository repo;

    private static final Set<String> OPEN   = Set.of("NEW", "PARTIALLY_FILLED");
    @SuppressWarnings("unused")
    private static final Set<String> CLOSED = Set.of("FILLED", "CANCELED", "REJECTED", "EXPIRED");

    @Transactional
    public ExchangeOrderEntity savePlaced(Long chatId,
                                          String exchange,
                                          NetworkType network,
                                          OrderRequest req,
                                          OrderResponse resp,
                                          BigDecimal pnl,
                                          BigDecimal pnlPct) {
        Instant now = Instant.now();

        String respOrderId = safeStr(callGetter(resp, "getOrderId"));
        ExchangeOrderEntity e = repo
                .findByChatIdAndExchangeAndNetworkAndOrderId(chatId, exchange, network, respOrderId)
                .orElseGet(ExchangeOrderEntity::new);

        e.setChatId(chatId);
        e.setExchange(exchange);
        e.setNetwork(network);

        // Биржевые идентификаторы
        e.setOrderId(respOrderId);
        // setClientOrderId может не существовать — ставим «мягко»
        String clientId = extractClientOrderId(resp, req);
        trySetClientOrderId(e, clientId);

        // Символ
        String symbol = firstNonBlank(
                safeStr(callGetter(resp, "getSymbol")),
                safeStr(callGetter(req,  "getSymbol"))
        );
        e.setSymbol(symbol);

        // Сторона/тип (enum либо String)
        e.setSide(firstNonBlank(
                callEnumName(req, "getSide"),
                safeStr(callGetter(req, "getSide"))
        ));
        e.setType(firstNonBlank(
                callEnumName(req, "getType"),
                safeStr(callGetter(req, "getType"))
        ));

        // Цена/кол-во из Request (то, что мы ставили)
        e.setPrice((BigDecimal) callGetter(req, "getPrice"));
        e.setQuantity((BigDecimal) callGetter(req, "getQuantity"));

        // Исполнение / статус
        e.setExecutedQty((BigDecimal) callGetter(resp, "getExecutedQty"));
        String status = safeStr(callGetter(resp, "getStatus"));
        e.setStatus(status.isBlank() ? "NEW" : status);

        // Комиссии (если есть)
        e.setCommission( (BigDecimal) callGetter(resp, "getCommission") );
        e.setCommissionAsset( safeStr(callGetter(resp, "getCommissionAsset")) );

        // PnL (если считали на уровне сервиса-исполнителя)
        e.setPnl(pnl);
        e.setPnlPct(pnlPct);

        if (e.getCreatedAt() == null) {
            e.setCreatedAt(now);
        }
        e.setUpdatedAt(now);
        e.setLastCheckedAt(now);

        return repo.save(e);
    }

    public List<ExchangeOrderEntity> findOpenByChatAndSymbol(Long chatId, String symbol) {
        return repo.findByChatIdAndSymbolAndStatusIn(chatId, symbol, OPEN);
    }

    public List<ExchangeOrderEntity> findRecentFilled(Long chatId, String symbol, int limit) {
        return repo.findByChatIdAndSymbolAndStatusOrderByUpdatedAtDesc(
                chatId, symbol, "FILLED", PageRequest.of(0, Math.max(1, limit))
        );
    }

    // ===================== helpers =====================

    /**
     * Пробуем вытащить clientOrderId из OrderResponse, а если там нет —
     * берём из OrderRequest. Поддерживаем распространённые варианты имён.
     */
    private String extractClientOrderId(OrderResponse resp, OrderRequest req) {
        // Популярные имена в response:
        String id = firstNonBlank(
                safeStr(callGetter(resp, "getClientOrderId")),
                safeStr(callGetter(resp, "getOrigClientOrderId")),
                safeStr(callGetter(resp, "getOrderLinkId")),
                safeStr(callGetter(resp, "getClientOrderID")),
                safeStr(callGetter(resp, "getOrigClientOrderID"))
        );
        if (!id.isBlank()) return id;

        // Популярные имена в request:
        id = firstNonBlank(
                safeStr(callGetter(req, "getClientOrderId")),
                safeStr(callGetter(req, "getClientOrderID")),
                safeStr(callGetter(req, "getOrderLinkId"))
        );
        return id;
    }

    /** Мягко проставить clientOrderId в сущность, если есть подходящий сеттер. */
    private void trySetClientOrderId(ExchangeOrderEntity e, String clientId) {
        if (clientId == null || clientId.isBlank()) return;
        // Возможные варианты имён сеттера в сущности:
        trySet(e, "setClientOrderId", clientId);
        trySet(e, "setOrigClientOrderId", clientId);
        trySet(e, "setOrderLinkId", clientId);
        trySet(e, "setClientOrderID", clientId);
        trySet(e, "setOrigClientOrderID", clientId);
    }

    /** Универсальный безопасный вызов сеттера (String). */
    private void trySet(Object target, String setter, String value) {
        if (target == null) return;
        try {
            Method m = target.getClass().getMethod(setter, String.class);
            m.invoke(target, value);
        } catch (Exception ignored) {
            // сеттера с таким именем нет — это нормально
        }
    }

    /** Безопасный вызов геттера через рефлексию. Возвращает null, если метода нет/ошибка. */
    private Object callGetter(Object target, String getter) {
        if (target == null) return null;
        try {
            Method m = target.getClass().getMethod(getter);
            return m.invoke(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Если геттер возвращает enum — возвращаем name(); иначе — null/строку. */
    private String callEnumName(Object target, String getter) {
        Object val = callGetter(target, getter);
        if (val == null) return null;
        if (val instanceof Enum<?> en) return en.name();
        try {
            Method name = val.getClass().getMethod("name");
            Object n = name.invoke(val);
            return n == null ? null : n.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String safeStr(Object v) {
        return v == null ? "" : v.toString();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String s : values) {
            if (s != null && !s.isBlank()) return s;
        }
        return "";
    }
}
