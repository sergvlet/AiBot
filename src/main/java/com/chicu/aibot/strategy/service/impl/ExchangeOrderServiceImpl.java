package com.chicu.aibot.strategy.service.impl;

import com.chicu.aibot.exchange.client.ExchangeClient;
import com.chicu.aibot.exchange.client.ExchangeClientFactory;
import com.chicu.aibot.exchange.enums.OrderSide;
import com.chicu.aibot.exchange.model.*;
import com.chicu.aibot.exchange.order.model.ExchangeOrderEntity;
import com.chicu.aibot.exchange.order.repository.ExchangeOrderRepository;
import com.chicu.aibot.exchange.service.ExchangeSettingsService;
import com.chicu.aibot.strategy.model.Order;
import com.chicu.aibot.strategy.service.OrderService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/**
 * Унифицированный сервис размещения/сопровождения ордеров для всех стратегий.
 * - MARKET: нормализация количества под баланс и min notional (с мягкими фолбэками)
 * - LIMIT: предварительная проверка баланса
 * - Унификация статусов и гашение -2013 ("Order does not exist") при опросе
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeOrderServiceImpl implements OrderService {

    private final ExchangeClientFactory clientFactory;
    private final ExchangeSettingsService settingsService;
    private final ExchangeOrderRepository orderRepo;

    /* ================= helpers ================= */

    /** Хвосты для распознавания BASE/QUOTE (фолбэк, если из клиента метаданные недоступны). */
    private static final List<String> KNOWN_QUOTES = List.of(
            "USDT", "USDC", "FDUSD", "BUSD", "TUSD", "DAI",
            "BTC", "ETH", "BNB",
            "EUR", "USD", "TRY", "BRL", "GBP", "AUD", "UAH", "RUB",
            "TRX", "XRP", "DOGE", "SOL", "ADA"
    );

    /** Пытаемся определить base/quote по символу; если клиент умеет — используйте его возможности (TODO). */
    private String[] splitSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) return new String[]{"", ""};
        for (String q : KNOWN_QUOTES) {
            if (symbol.endsWith(q) && symbol.length() > q.length()) {
                return new String[]{symbol.substring(0, symbol.length() - q.length()), q};
            }
        }
        // fallback: не распознали — считаем, что всё это BASE, а QUOTE неизвестна
        return new String[]{symbol, ""};
    }

    private String normalizeStatus(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toUpperCase(Locale.ROOT).replace(" ", "").replace("_", "");
        return switch (s) {
            case "FILLED" -> "FILLED";
            case "PARTIALLYFILLED" -> "PARTIALLY_FILLED";
            case "NEW" -> "NEW";
            case "CANCELLED", "CANCELED" -> "CANCELED";
            case "REJECTED" -> "REJECTED";
            case "EXPIRED" -> "EXPIRED";
            default -> s;
        };
    }

    private double toDouble(BigDecimal v) { return v == null ? 0.0 : v.doubleValue(); }

    private BigDecimal getFree(AccountInfo info, String asset) {
        if (info == null || info.getBalances() == null || asset == null || asset.isBlank()) return BigDecimal.ZERO;
        for (Balance b : info.getBalances()) {
            if (asset.equalsIgnoreCase(b.getAsset())) {
                return Optional.ofNullable(b.getFree()).orElse(BigDecimal.ZERO);
            }
        }
        return BigDecimal.ZERO;
    }

    /* ================= pre-checks ================= */

    /** Бросает IllegalStateException, если баланса недостаточно для LIMIT. */
    private void precheckLimit(
            String symbol, Order.Side side, double price, double quantity,
            AccountInfo acc
    ) {
        String[] pq = splitSymbol(symbol);
        String base = pq[0], quote = pq[1];

        BigDecimal qty = BigDecimal.valueOf(quantity);
        BigDecimal pr  = BigDecimal.valueOf(price);

        if (side == Order.Side.SELL) {
            BigDecimal baseFree = getFree(acc, base);
            if (baseFree.compareTo(qty) < 0) {
                throw new IllegalStateException(
                        "Недостаточно " + base + " для LIMIT SELL: нужно " + qty + ", доступно " + baseFree);
            }
        } else {
            if (!quote.isBlank()) {
                BigDecimal needQuote = pr.multiply(qty);
                BigDecimal quoteFree = getFree(acc, quote);
                if (quoteFree.compareTo(needQuote) < 0) {
                    throw new IllegalStateException(
                            "Недостаточно " + quote + " для LIMIT BUY: нужно ~" + needQuote + ", доступно " + quoteFree);
                }
            }
        }
    }

    /** Контекст нормализации для MARKET: итоговое qty и референтная цена. qty==0 → пропуск сделки. */
    private record MarketCtx(double qty, double refPrice) {}

    /**
     * MARKET-нормализация количества под доступный баланс и min notional (с фолбэком по котируемой валюте).
     * Исключения не бросает — возвращает qty=0.0 если выполнить нельзя (мягкий отказ).
     */
    private MarketCtx precheckAndNormalizeMarket(
            ExchangeClient client, ExchangeSettings settings, ExchangeApiKey keys,
            String symbol, Order.Side side, double qtyRequested
    ) {
        if (qtyRequested <= 0) {
            log.warn("Количество должно быть > 0 для MARKET {}, запрос={} → пропуск.", symbol, qtyRequested);
            return new MarketCtx(0.0, 0.0);
        }

        String[] pq = splitSymbol(symbol);
        String base = pq[0], quote = pq[1];

        // Текущая цена
        Optional<TickerInfo> t = client.getTicker(symbol, settings.getNetwork());
        double price = toDouble(t.get().getPrice());
        if (price <= 0) {
            log.warn("Не удалось получить цену для {} (price={}) → пропуск MARKET.", symbol, price);
            return new MarketCtx(0.0, 0.0);
        }

        // Балансы
        AccountInfo acc = client.fetchAccountInfo(keys.getPublicKey(), keys.getSecretKey(), settings.getNetwork());
        double baseFree  = toDouble(getFree(acc, base));
        double quoteFree = toDouble(getFree(acc, quote));

        // Базовая нормализация под баланс
        double qty = qtyRequested;
        if (side == Order.Side.SELL) {
            if (baseFree <= 0.0) {
                log.info("Недостаточно {} для MARKET SELL {}: доступно 0 → пропуск.", base, symbol);
                return new MarketCtx(0.0, price);
            }
            qty = Math.min(qty, baseFree);
        } else { // BUY
            if (!quote.isBlank()) {
                double maxByFunds = quoteFree / price;
                if (maxByFunds <= 0.0) {
                    log.info("Недостаточно {} для MARKET BUY {}: доступно {} → пропуск.",
                            quote, symbol, format2(quoteFree));
                    return new MarketCtx(0.0, price);
                }
                qty = Math.min(qty, maxByFunds);
            }
        }

        // Требование по минимальному ноционалу (фолбэк для популярных стейблов ≈ 10)
        double minNotional = minNotionalForQuote(quote);
        if (minNotional > 0 && qty * price < minNotional) {
            double needQty = minNotional / price;

            if (side == Order.Side.BUY) {
                double maxByFunds = quoteFree / price;
                if (maxByFunds + 1e-12 < needQty) {
                    log.info("Недостаточно {} для MARKET BUY {} с min notional {} {} (доступно {}). Пропуск.",
                            quote, symbol, format2(minNotional), quote, format2(quoteFree));
                    return new MarketCtx(0.0, price);
                }
            } else { // SELL
                if (baseFree + 1e-12 < needQty) {
                    log.info("Недостаточно {} для MARKET SELL {} с min notional {} {} (доступно {}). Пропуск.",
                            base, symbol, format2(minNotional), quote, format6(baseFree));
                    return new MarketCtx(0.0, price);
                }
            }
            qty = Math.max(qty, needQty);
        }

        // Округление количества ВНИЗ, чтобы не упереться в LOT_SIZE (универсально: 6 знаков)
        qty = roundDown(qty);

        if (qty <= 0.0) {
            log.info("Количество после нормализации стало 0 для {} → пропуск MARKET.", symbol);
            return new MarketCtx(0.0, price);
        }
        if (minNotional > 0 && qty * price + 1e-9 < minNotional) {
            log.info("Не удаётся выполнить min notional {} для {} → пропуск MARKET.", format2(minNotional), symbol);
            return new MarketCtx(0.0, price);
        }

        log.info("Нормализовано qty для {} {}: qty={} (price={}, notional={})",
                side, symbol, format6(qty), format6(price), format2(qty * price));
        return new MarketCtx(qty, price);
    }

    private static double minNotionalForQuote(String quote) {
        if (quote == null) return 0.0;
        String q = quote.toUpperCase(Locale.ROOT);
        return (q.equals("USDT") || q.equals("FDUSD") || q.equals("BUSD") || q.equals("USDC") || q.equals("TUSD")) ? 10.0 : 0.0;
    }

    private static double roundDown(double v) {
        return BigDecimal.valueOf(v).setScale(6, RoundingMode.DOWN).doubleValue();
    }

    private static String format2(double v) { return String.format("%,.2f", v); }
    private static String format6(double v) { return String.format("%,.6f", v); }

    private static boolean isOrderNotExistError(Throwable e) {
        if (e == null) return false;
        String msg = String.valueOf(e.getMessage());
        return msg.contains("\"code\":-2013") || msg.toLowerCase(Locale.ROOT).contains("order does not exist");
    }

    /* ================= API ================= */

    @Override
    @Transactional
    public Order placeLimit(Long chatId, String symbol, Order.Side side, double price, double quantity) {
        log.info("Лимитный ордер → chatId={}, {} {} @{} qty={}", chatId, side, symbol, price, quantity);

        var settings = settingsService.getOrCreate(chatId);
        var keys     = settingsService.getApiKey(chatId);
        var client   = clientFactory.getClient(settings.getExchange());

        // PRE-CHECK баланса
        AccountInfo acc = client.fetchAccountInfo(keys.getPublicKey(), keys.getSecretKey(), settings.getNetwork());
        precheckLimit(symbol, side, price, quantity, acc);

        var req = OrderRequest.builder()
                .symbol(symbol)
                .side(side == Order.Side.BUY ? OrderSide.BUY : OrderSide.SELL)
                .type(com.chicu.aibot.exchange.enums.OrderType.LIMIT)
                .quantity(BigDecimal.valueOf(quantity))
                .price(BigDecimal.valueOf(price))
                .build();

        var resp = client.placeOrder(keys.getPublicKey(), keys.getSecretKey(), settings.getNetwork(), req);

        String statusNorm = normalizeStatus(resp.getStatus());
        double executed   = toDouble(resp.getExecutedQty());
        boolean filled    = "FILLED".equals(statusNorm);

        // ✅ Сохраняем LIMIT-ордер в БД (цена — из запроса)
        Instant now = Instant.now();
        try {
            ExchangeOrderEntity entity = ExchangeOrderEntity.builder()
                    .chatId(chatId)
                    .exchange(String.valueOf(settings.getExchange()))
                    .network(settings.getNetwork())
                    .orderId(resp.getOrderId())
                    .symbol(resp.getSymbol())
                    .side(side.name())
                    .type("LIMIT")
                    .price(BigDecimal.valueOf(price))
                    .quantity(BigDecimal.valueOf(quantity))
                    .executedQty(resp.getExecutedQty())
                    .status(statusNorm)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            orderRepo.save(entity);
        } catch (Exception e) {
            log.warn("Не удалось сохранить LIMIT-ордер {} в БД: {}", resp.getOrderId(), e.getMessage());
        }

        log.info("Лимитный ордер выставлен: id={}, status={}, executedQty={}", resp.getOrderId(), statusNorm, executed);

        return new Order(
                resp.getOrderId(),
                resp.getSymbol(),
                side,
                price,
                executed,
                filled,
                false,
                false
        );
    }

    @Override
    @Transactional
    public Order placeMarket(Long chatId, String symbol, Order.Side side, double quantity) {
        log.info("Рыночный ордер → chatId={}, {} {} qty={}", chatId, side, symbol, quantity);

        var settings = settingsService.getOrCreate(chatId);
        var keys     = settingsService.getApiKey(chatId);
        var client   = clientFactory.getClient(settings.getExchange());

        // нормализуем и получаем референтную цену
        MarketCtx ctx = precheckAndNormalizeMarket(client, settings, keys, symbol, side, quantity);
        if (ctx.qty() <= 0.0) {
            // мягкий пропуск вместо исключения
            return new Order(null, symbol, side, 0.0, 0.0, false, false, false);
        }

        var req = OrderRequest.builder()
                .symbol(symbol)
                .side(side == Order.Side.BUY ? OrderSide.BUY : OrderSide.SELL)
                .type(com.chicu.aibot.exchange.enums.OrderType.MARKET)
                .quantity(BigDecimal.valueOf(ctx.qty()))
                .build();

        var resp = client.placeOrder(keys.getPublicKey(), keys.getSecretKey(), settings.getNetwork(), req);

        String statusNorm = normalizeStatus(resp.getStatus());
        double executed   = toDouble(resp.getExecutedQty());
        boolean filled    = "FILLED".equals(statusNorm);

        // ✅ Сохраняем MARKET-ордер (price — референтная из тикера; у resp её может не быть)
        Instant now = Instant.now();
        try {
            ExchangeOrderEntity entity = ExchangeOrderEntity.builder()
                    .chatId(chatId)
                    .exchange(String.valueOf(settings.getExchange()))
                    .network(settings.getNetwork())
                    .orderId(resp.getOrderId())
                    .symbol(resp.getSymbol())
                    .side(side.name())
                    .type("MARKET")
                    .price(ctx.refPrice() > 0 ? BigDecimal.valueOf(ctx.refPrice()) : null)
                    .quantity(BigDecimal.valueOf(ctx.qty()))
                    .executedQty(resp.getExecutedQty())
                    .status(statusNorm)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            orderRepo.save(entity);
        } catch (Exception e) {
            log.warn("Не удалось сохранить MARKET-ордер {} в БД: {}", resp.getOrderId(), e.getMessage());
        }

        log.info("Рыночный ордер: id={}, status={}, executedQty={}", resp.getOrderId(), statusNorm, executed);

        double uiPrice = ctx.refPrice();
        return new Order(resp.getOrderId(), resp.getSymbol(), side, uiPrice, executed, filled, false, false);
    }

    @Override
    public void cancel(Long chatId, Order order) {
        log.info("Отмена ордера → id={}, symbol={}", order.getId(), order.getSymbol());

        var settings = settingsService.getOrCreate(chatId);
        var keys     = settingsService.getApiKey(chatId);
        var client   = clientFactory.getClient(settings.getExchange());

        // Узнаём актуальный статус у биржи (если доступен)
        String statusNorm = "";
        try {
            var opt = client.fetchOrder(
                    keys.getPublicKey(), keys.getSecretKey(), settings.getNetwork(), order.getSymbol(), order.getId());
            if (opt.isPresent()) {
                statusNorm = normalizeStatus(opt.get().getStatus());
            } else {
                // биржа не знает заказ — считаем, что его уже нет (чтобы не зацикливаться)
                statusNorm = "CANCELED";
            }
        } catch (Exception e) {
            if (isOrderNotExistError(e)) {
                statusNorm = "CANCELED";
                log.info("cancel(): биржа вернула 'order does not exist', считаем отменённым: {}", order.getId());
            } else {
                log.debug("cancel(): не удалось получить статус ордера {}: {}", order.getId(), e.getMessage());
            }
        }

        // Пропускаем отмену для терминальных статусов
        if ("FILLED".equals(statusNorm) || "CANCELED".equals(statusNorm)
                || "EXPIRED".equals(statusNorm) || "REJECTED".equals(statusNorm)) {
            log.info("Отмена пропущена: id={} status={}", order.getId(), statusNorm);
            order.setCancelled("CANCELED".equals(statusNorm) || "EXPIRED".equals(statusNorm) || "REJECTED".equals(statusNorm));
            return;
        }

        try {
            boolean ok = client.cancelOrder(
                    keys.getPublicKey(), keys.getSecretKey(),
                    settings.getNetwork(), order.getSymbol(), order.getId()
            );
            if (ok) order.setCancelled(true);
            log.info("Отмена ордера id={} {}", order.getId(), ok ? "успех" : "не выполнена");
        } catch (Exception e) {
            if (isOrderNotExistError(e)) {
                order.setCancelled(true);
                log.info("cancel(): 'order does not exist' при отмене — помечаем отменённым: {}", order.getId());
            } else {
                log.warn("Ошибка отмены ордера id={}: {}", order.getId(), e.getMessage());
            }
        }
    }

    @Override
    public void closePosition(Long chatId, Order order) {
        log.info("Закрыть позицию → id={}, symbol={}, side={}, volume={}",
                order.getId(), order.getSymbol(), order.getSide(), order.getVolume());

        if (order.getVolume() <= 0.0) {
            log.warn("Пропуск закрытия: у ордера id={} нет исполненного объёма (volume={})",
                    order.getId(), order.getVolume());
            return;
        }

        Order.Side opposite = (order.getSide() == Order.Side.BUY) ? Order.Side.SELL : Order.Side.BUY;
        Order market = placeMarket(chatId, order.getSymbol(), opposite, order.getVolume());

        if (market.getVolume() > 0.0) {
            order.setClosed(true);
            log.info("Позиция закрыта: id={}, closedQty={}", order.getId(), market.getVolume());
        } else {
            log.warn("Не удалось закрыть позицию id={}: market executedQty=0 (filled={})",
                    order.getId(), market.isFilled());
        }
    }

    /* ================= strategy helpers ================= */

    @Override
    public List<Order> loadActiveOrders(Long chatId, String symbol) {
        var settings = settingsService.getOrCreate(chatId);
        var keys     = settingsService.getApiKey(chatId);
        var client   = clientFactory.getClient(settings.getExchange());

        List<Order> result = new ArrayList<>();
        try {
            List<OrderInfo> open = client.fetchOpenOrders(
                    keys.getPublicKey(), keys.getSecretKey(), settings.getNetwork(), symbol);

            for (OrderInfo oi : open) {
                Order.Side side = (oi.getSide() == OrderSide.BUY) ? Order.Side.BUY : Order.Side.SELL;

                double price   = oi.getPrice() == null ? 0.0 : oi.getPrice().doubleValue();
                double execQty = oi.getExecutedQty() == null ? 0.0 : oi.getExecutedQty().doubleValue();

                String statusNorm = normalizeStatus(oi.getStatus());
                boolean filled    = "FILLED".equals(statusNorm);

                result.add(new Order(
                        oi.getOrderId(),
                        oi.getSymbol(),
                        side,
                        price,
                        execQty,
                        filled,
                        false,
                        false
                ));
            }
            log.info("Открытые ордера: {} шт по {} (chatId={})", result.size(), symbol, chatId);
        } catch (Exception e) {
            log.warn("loadActiveOrders({}, {}) ошибка: {}", chatId, symbol, e.getMessage());
        }
        return result;
    }

    @Override
    public void refreshOrderStatuses(Long chatId, String symbol, List<Order> localOrders) {
        if (localOrders == null || localOrders.isEmpty()) return;

        var settings = settingsService.getOrCreate(chatId);
        var keys     = settingsService.getApiKey(chatId);
        var client   = clientFactory.getClient(settings.getExchange());

        for (Order o : localOrders) {
            if (o.isCancelled() || o.isClosed()) continue;
            String id = o.getId();
            if (id == null) continue;

            try {
                var opt = client.fetchOrder(
                        keys.getPublicKey(), keys.getSecretKey(), settings.getNetwork(), symbol, id);
                if (opt.isEmpty()) {
                    // Биржа не знает ордер — перестанем его опрашивать
                    o.setCancelled(true);
                    log.info("refresh: биржа не вернула ордер (treat as canceled) id={}", id);
                    continue;
                }

                OrderInfo st    = opt.get();
                String status   = normalizeStatus(st.getStatus());
                double executed = st.getExecutedQty() == null ? 0.0 : st.getExecutedQty().doubleValue();
                if (executed > 0.0) {
                    o.setVolume(executed);
                }

                switch (status) {
                    case "FILLED" -> o.setFilled(true);
                    case "PARTIALLY_FILLED" -> { /* уже учли объём */ }
                    case "CANCELED", "REJECTED", "EXPIRED" -> o.setCancelled(true);
                    default -> { /* NEW/прочее — как есть */ }
                }
            } catch (Exception e) {
                if (isOrderNotExistError(e)) {
                    o.setCancelled(true);
                    log.info("refresh: 'order does not exist' → помечаем отменённым id={}", id);
                } else {
                    log.debug("refreshOrderStatuses: id={} ошибка: {}", id, e.getMessage());
                }
            }
        }
    }
}
