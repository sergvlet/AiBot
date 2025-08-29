package com.chicu.aibot.exchange.service.impl;

import com.chicu.aibot.bot.menu.feature.ai.strategy.view.LiveSnapshot;
import com.chicu.aibot.exchange.client.ExchangeClient;
import com.chicu.aibot.exchange.client.ExchangeClientFactory;
import com.chicu.aibot.exchange.model.*;
import com.chicu.aibot.exchange.service.ExchangeSettingsService;
import com.chicu.aibot.exchange.service.MarketLiveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketLiveServiceImpl implements MarketLiveService {

    private final ExchangeSettingsService settingsService;
    private final ExchangeClientFactory clientFactory;

    @Override
    public LiveSnapshot build(Long chatId, String symbol) {
        var settings = settingsService.getOrCreate(chatId);
        var keys     = settingsService.getApiKey(chatId);
        ExchangeClient client = clientFactory.getClient(settings.getExchange());

        // === тикер ===
        Optional<TickerInfo> opt = client.getTicker(symbol, settings.getNetwork());
        if (opt.isEmpty() || opt.get().getPrice() == null || opt.get().getChangePct() == null) {
            log.info("LiveSnapshot: символ {} недоступен на {} {}", symbol, settings.getExchange(), settings.getNetwork());
            return LiveSnapshot.builder()
                    .priceStr("❌ Пара " + symbol + " недоступна на "
                              + settings.getExchange() + " " + settings.getNetwork())
                    .changePct(0)
                    .base("-").quote("-")
                    .baseBal("0").quoteBal("0")
                    .openCount(0)
                    .openOrdersBlock("")
                    .lastPrice(0)
                    .build();
        }

        TickerInfo t = opt.get();
        double lastPrice = safeDouble(t.getPrice());
        String priceStr  = fmt(t.getPrice());

        double changePct = normalizePct(safeDouble(t.getChangePct()));

        // === BASE / QUOTE ===
        String[] pq = splitSymbol(symbol);
        String base = pq[0], quote = pq[1];

        // === балансы ===
        String baseBal = "0", quoteBal = "0";
        try {
            AccountInfo ai = client.fetchAccountInfo(keys.getPublicKey(), keys.getSecretKey(), settings.getNetwork());
            for (BalanceInfo b : ai.getBalances()) {
                if (b.getAsset().equalsIgnoreCase(base))  baseBal  = fmt(b.getFree());
                if (b.getAsset().equalsIgnoreCase(quote)) quoteBal = fmt(b.getFree());
            }
        } catch (Exception e) {
            log.warn("Не удалось получить балансы для {}: {}", symbol, e.getMessage());
        }

        // === открытые ордера ===
        StringBuilder ob = new StringBuilder();
        try {
            List<OrderInfo> open = client.fetchOpenOrders(
                    keys.getPublicKey(), keys.getSecretKey(), settings.getNetwork(), symbol);
            open.sort(Comparator.comparing(OrderInfo::getOrderId));

            for (OrderInfo o : open) {
                String p = o.getPrice() == null ? "-" : fmt(o.getPrice());
                String q = o.getExecutedQty() == null ? "0" : fmt(o.getExecutedQty());
                ob.append("• #").append(o.getOrderId())
                        .append(" ").append(o.getSide().name())
                        .append(" @").append(p)
                        .append(" exec=").append(q)
                        .append(" [").append(o.getStatus()).append("]\n");
            }
        } catch (Exception e) {
            log.warn("Не удалось получить открытые ордера по {}: {}", symbol, e.getMessage());
        }

        return LiveSnapshot.builder()
                .priceStr(priceStr)
                .changePct(changePct)
                .base(base).quote(quote)
                .baseBal(baseBal).quoteBal(quoteBal)
                .openCount(ob.isEmpty() ? 0 : ob.toString().split("\n").length)
                .openOrdersBlock(ob.toString().trim())
                .lastPrice(lastPrice)
                .build();
    }

    /** Деление символа на base/quote по известным суффиксам. */
    private static String[] splitSymbol(String symbol) {
        String[] knownQuotes = {"USDT","FDUSD","BUSD","USDC","TUSD","BTC","ETH"};
        for (String q : knownQuotes) {
            if (symbol.endsWith(q)) {
                return new String[]{symbol.substring(0, symbol.length() - q.length()), q};
            }
        }
        // fallback: делим пополам
        int mid = symbol.length() / 2;
        return new String[]{symbol.substring(0, mid), symbol.substring(mid)};
    }

    private static double safeDouble(BigDecimal v) {
        return v == null ? 0.0 : v.doubleValue();
    }

    private static double normalizePct(double raw) {
        return Math.abs(raw) <= 1.0 ? raw * 100.0 : raw;
    }

    private static String fmt(BigDecimal v) {
        if (v == null) return "-";
        return new DecimalFormat("#,##0.########").format(v);
    }
}
