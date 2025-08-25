package com.chicu.aibot.exchange.service.impl;

import com.chicu.aibot.bot.menu.feature.ai.strategy.view.LiveSnapshot;
import com.chicu.aibot.exchange.client.ExchangeClient;
import com.chicu.aibot.exchange.client.ExchangeClientFactory;
import com.chicu.aibot.exchange.model.*;
import com.chicu.aibot.exchange.service.ExchangeSettingsService;
import com.chicu.aibot.exchange.service.MarketLiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MarketLiveServiceImpl implements MarketLiveService {

    private final ExchangeSettingsService settingsService;
    private final ExchangeClientFactory clientFactory;

    @Override
    public LiveSnapshot build(Long chatId, String symbol) {
        var settings = settingsService.getOrCreate(chatId);
        var keys     = settingsService.getApiKey(chatId);
        ExchangeClient client = clientFactory.getClient(settings.getExchange());

        // тикер
        TickerInfo t = client.getTicker(symbol, settings.getNetwork());
        double lastPrice = t.getPrice().doubleValue();
        String priceStr  = fmt(t.getPrice());

        double raw = t.getChangePct() == null ? 0.0 : t.getChangePct().doubleValue();
        double changePct = Math.abs(raw) <= 1.0 ? raw * 100.0 : raw; // нормализация %

        // BASE / QUOTE
        String[] pq = splitSymbol(symbol);
        String base = pq[0], quote = pq[1];

        // балансы
        AccountInfo ai = client.fetchAccountInfo(keys.getPublicKey(), keys.getSecretKey(), settings.getNetwork());
        String baseBal = "0", quoteBal = "0";
        for (Balance b : ai.getBalances()) {
            if (b.getAsset().equalsIgnoreCase(base))  baseBal  = fmt(b.getFree());
            if (b.getAsset().equalsIgnoreCase(quote)) quoteBal = fmt(b.getFree());
        }

        // открытые ордера (для справки в панелях)
        List<OrderInfo> open = client.fetchOpenOrders(
                keys.getPublicKey(), keys.getSecretKey(), settings.getNetwork(), symbol);
        open.sort(Comparator.comparing(OrderInfo::getOrderId));

        StringBuilder ob = new StringBuilder();
        for (OrderInfo o : open) {
            String p = o.getPrice() == null ? "-" : fmt(o.getPrice());
            String q = o.getExecutedQty() == null ? "0" : fmt(o.getExecutedQty());
            ob.append("• #").append(o.getOrderId())
              .append(" ").append(o.getSide().name())
              .append(" @").append(p)
              .append(" exec=").append(q)
              .append(" [").append(o.getStatus()).append("]\n");
        }

        return LiveSnapshot.builder()
                .priceStr(priceStr)
                .changePct(changePct)
                .base(base).quote(quote)
                .baseBal(baseBal).quoteBal(quoteBal)
                .openCount(open.size())
                .openOrdersBlock(ob.toString().trim())
                .lastPrice(lastPrice)
                .build();
    }

    private static String[] splitSymbol(String symbol) {
        String[] qs = {"USDT","FDUSD","BUSD","USDC","TUSD","BTC","ETH"};
        for (String q : qs) if (symbol.endsWith(q))
            return new String[]{symbol.substring(0, symbol.length() - q.length()), q};
        int mid = Math.max(3, symbol.length() - 4);
        return new String[]{symbol.substring(0, mid), symbol.substring(mid)};
    }

    private static String fmt(BigDecimal v) { return new DecimalFormat("#,##0.########").format(v); }
}
