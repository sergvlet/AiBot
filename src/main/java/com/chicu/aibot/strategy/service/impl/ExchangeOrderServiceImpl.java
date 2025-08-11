package com.chicu.aibot.strategy.service.impl;

import com.chicu.aibot.exchange.client.ExchangeClient;
import com.chicu.aibot.exchange.client.ExchangeClientFactory;
import com.chicu.aibot.exchange.enums.OrderSide;
import com.chicu.aibot.exchange.model.*;
import com.chicu.aibot.exchange.service.ExchangeSettingsService;
import com.chicu.aibot.strategy.model.Order;
import com.chicu.aibot.strategy.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeOrderServiceImpl implements OrderService {

    private final ExchangeClientFactory clientFactory;
    private final ExchangeSettingsService settingsService;

    /* ================= helpers ================= */

    /** Хвосты котируемых валют, чтобы распарсить BASE/QUOTE из символа вида ETHUSDT, BTCUSDC, ETHBTC и т.п. */
    private static final List<String> KNOWN_QUOTES = List.of(
            "USDT", "USDC", "BUSD", "FDUSD", "TUSD", "DAI",
            "BTC", "ETH", "BNB",
            "EUR", "USD", "TRY", "BRL", "GBP", "AUD", "UAH", "RUB",
            "TRX", "XRP", "DOGE", "SOL", "ADA"
    );

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

    /** Бросает IllegalStateException, если баланса недостаточно. Возвращает оценочную цену (для MARKET). */
    private void precheckLimit(
            String symbol, Order.Side side, double price, double quantity,
            AccountInfo acc, ExchangeClient client, ExchangeSettings settings
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
            // BUY: проверим котируемую валюту
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

    private void precheckMarket(
            String symbol, Order.Side side, double quantity,
            AccountInfo acc, ExchangeClient client, ExchangeSettings settings
    ) {
        String[] pq   = splitSymbol(symbol);
        String base   = pq[0], quote = pq[1];
        BigDecimal qty = BigDecimal.valueOf(quantity);

        if (side == Order.Side.SELL) {
            BigDecimal baseFree = getFree(acc, base);
            if (baseFree.compareTo(qty) < 0) {
                throw new IllegalStateException(
                        "Недостаточно " + base + " для MARKET SELL: нужно " + qty + ", доступно " + baseFree);
            }
        } else {
            // MARKET BUY: оценим цену по тикеру
            if (!quote.isBlank()) {
                TickerInfo t = client.getTicker(symbol, settings.getNetwork());
                BigDecimal px = Optional.ofNullable(t.getPrice()).orElse(BigDecimal.ZERO);
                if (px.signum() <= 0) {
                    log.warn("Не удалось получить цену для MARKET BUY {}, пропускаю pre-check котируемой валюты", symbol);
                    return; // не стопорим, но предупредим
                }
                BigDecimal needQuote = px.multiply(qty);
                BigDecimal quoteFree = getFree(acc, quote);
                if (quoteFree.compareTo(needQuote) < 0) {
                    throw new IllegalStateException(
                            "Недостаточно " + quote + " для MARKET BUY: нужно ~" + needQuote + ", доступно " + quoteFree);
                }
            }
        }
    }

    /* ================= API ================= */

    @Override
    public Order placeLimit(Long chatId, String symbol, Order.Side side, double price, double quantity) {
        log.info("Лимитный ордер → chatId={}, {} {} @{} qty={}", chatId, side, symbol, price, quantity);

        ExchangeSettings settings = settingsService.getOrCreate(chatId);
        ExchangeApiKey   keys     = settingsService.getApiKey(chatId);
        var client                = clientFactory.getClient(settings.getExchange());

        // PRE-CHECK баланса
        AccountInfo acc = client.fetchAccountInfo(keys.getPublicKey(), keys.getSecretKey(), settings.getNetwork());
        precheckLimit(symbol, side, price, quantity, acc, client, settings);

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
    public Order placeMarket(Long chatId, String symbol, Order.Side side, double quantity) {
        log.info("Рыночный ордер → chatId={}, {} {} qty={}", chatId, side, symbol, quantity);

        ExchangeSettings settings = settingsService.getOrCreate(chatId);
        ExchangeApiKey   keys     = settingsService.getApiKey(chatId);
        ExchangeClient client                = clientFactory.getClient(settings.getExchange());

        // PRE-CHECK баланса
        AccountInfo acc = client.fetchAccountInfo(keys.getPublicKey(), keys.getSecretKey(), settings.getNetwork());
        precheckMarket(symbol, side, quantity, acc, client, settings);

        var req = OrderRequest.builder()
                .symbol(symbol)
                .side(side == Order.Side.BUY ? OrderSide.BUY : OrderSide.SELL)
                .type(com.chicu.aibot.exchange.enums.OrderType.MARKET)
                .quantity(BigDecimal.valueOf(quantity))
                .build();

        var resp = client.placeOrder(keys.getPublicKey(), keys.getSecretKey(), settings.getNetwork(), req);

        String statusNorm = normalizeStatus(resp.getStatus());
        double executed   = toDouble(resp.getExecutedQty());
        boolean filled    = "FILLED".equals(statusNorm);

        log.info("Рыночный ордер: id={}, status={}, executedQty={}", resp.getOrderId(), statusNorm, executed);

        return new Order(
                resp.getOrderId(),
                resp.getSymbol(),
                side,
                0.0,
                executed,
                filled,
                false,
                false
        );
    }

    @Override
    public void cancel(Long chatId, Order order) {
        log.info("Отмена ордера → id={}, symbol={}", order.getId(), order.getSymbol());

        ExchangeSettings settings = settingsService.getOrCreate(chatId);
        ExchangeApiKey   keys     = settingsService.getApiKey(chatId);
        var client                = clientFactory.getClient(settings.getExchange());

        try {
            boolean ok = client.cancelOrder(
                    keys.getPublicKey(), keys.getSecretKey(),
                    settings.getNetwork(), order.getSymbol(), order.getId()
            );
            if (ok) order.setCancelled(true);
            log.info("Отмена ордера id={} {}", order.getId(), ok ? "успех" : "не выполнена");
        } catch (Exception e) {
            log.warn("Ошибка отмены ордера id={}: {}", order.getId(), e.getMessage());
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
        ExchangeSettings settings = settingsService.getOrCreate(chatId);
        ExchangeApiKey   keys     = settingsService.getApiKey(chatId);
        var client                = clientFactory.getClient(settings.getExchange());

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

        ExchangeSettings settings = settingsService.getOrCreate(chatId);
        ExchangeApiKey   keys     = settingsService.getApiKey(chatId);
        var client                = clientFactory.getClient(settings.getExchange());

        for (Order o : localOrders) {
            if (o.isCancelled() || o.isClosed()) continue;
            String id = o.getId();
            if (id == null) continue;

            try {
                var opt = client.fetchOrder(
                        keys.getPublicKey(), keys.getSecretKey(), settings.getNetwork(), symbol, id);
                if (opt.isEmpty()) continue;

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
                log.debug("refreshOrderStatuses: id={} ошибка: {}", id, e.getMessage());
            }
        }
    }
}
