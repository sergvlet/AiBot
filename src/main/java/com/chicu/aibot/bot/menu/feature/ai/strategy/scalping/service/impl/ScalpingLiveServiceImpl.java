package com.chicu.aibot.bot.menu.feature.ai.strategy.scalping.service.impl;

import com.chicu.aibot.bot.menu.feature.ai.strategy.scalping.service.ScalpingLiveService;
import com.chicu.aibot.bot.menu.feature.ai.strategy.view.LiveSnapshot;
import com.chicu.aibot.exchange.client.ExchangeClient;
import com.chicu.aibot.exchange.client.ExchangeClientFactory;
import com.chicu.aibot.exchange.model.AccountInfo;
import com.chicu.aibot.exchange.model.Balance;
import com.chicu.aibot.exchange.model.OrderInfo;
import com.chicu.aibot.exchange.model.TickerInfo;
import com.chicu.aibot.exchange.service.ExchangeSettingsService;
import com.chicu.aibot.strategy.scalping.service.ScalpingStrategySettingsService;
import com.chicu.aibot.trading.trade.TradeLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScalpingLiveServiceImpl implements ScalpingLiveService {

    private final ExchangeSettingsService settingsService;
    private final ExchangeClientFactory clientFactory;
    private final ScalpingStrategySettingsService scalpingSettings; // можно удалить, если не нужен
    private final TradeLogService tradeLogService;                 // оставлен на будущее

    @Override
    public LiveSnapshot build(Long chatId, String symbol) {
        // настройки биржи/ключей
        var settings = settingsService.getOrCreate(chatId);
        var keys     = settingsService.getApiKey(chatId);
        ExchangeClient client = clientFactory.getClient(settings.getExchange());

        // Тикер
        TickerInfo t = client.getTicker(symbol, settings.getNetwork());
        double price = t.getPrice().doubleValue();
        String priceStr = fmtPrice(t.getPrice());

        // Нормализуем изменение: если пришла доля (0.0123), превращаем в проценты (1.23)
        double raw = t.getChangePct() == null ? 0.0 : t.getChangePct().doubleValue();
        double changePct = Math.abs(raw) <= 1.0 ? raw * 100.0 : raw;

        // Балансы
        String[] pq = splitSymbol(symbol); // [BASE, QUOTE]
        String base = pq[0], quote = pq[1];

        AccountInfo ai = client.fetchAccountInfo(keys.getPublicKey(), keys.getSecretKey(), settings.getNetwork());
        String baseBal = "0";
        String quoteBal = "0";
        for (Balance b : ai.getBalances()) {
            if (b.getAsset().equalsIgnoreCase(base))  baseBal  = fmtQty(b.getFree());
            if (b.getAsset().equalsIgnoreCase(quote)) quoteBal = fmtQty(b.getFree());
        }

        // Открытые ордера
        List<OrderInfo> open = client.fetchOpenOrders(keys.getPublicKey(), keys.getSecretKey(), settings.getNetwork(), symbol);
        open.sort(Comparator.comparing(OrderInfo::getOrderId));
        StringBuilder ob = new StringBuilder();
        for (OrderInfo o : open) {
            String side = o.getSide().name();
            String p = o.getPrice() == null ? "-" : fmtPrice(o.getPrice());
            String q = o.getExecutedQty() == null ? "0" : fmtQty(o.getExecutedQty());
            ob.append("• #").append(o.getOrderId())
              .append(" ").append(side)
              .append(" @").append(p)
              .append(" exec=").append(q)
              .append(" [").append(o.getStatus()).append("]\n");
        }

        return LiveSnapshot.builder()
                .priceStr(priceStr)
                .changePct(changePct)
                .base(base)
                .quote(quote)
                .baseBal(baseBal)
                .quoteBal(quoteBal)
                .openCount(open.size())
                .openOrdersBlock(ob.toString().trim())
                .lastPrice(price)
                .build();
    }

    private static String[] splitSymbol(String symbol) {
        // простая эвристика: конец — один из quote: USDT, FDUSD, BUSD, USDC, TUSD
        String[] qs = {"USDT","FDUSD","BUSD","USDC","TUSD","BTC","ETH"};
        for (String q : qs) {
            if (symbol.endsWith(q)) {
                return new String[]{symbol.substring(0, symbol.length()-q.length()), q};
            }
        }
        int mid = Math.max(3, symbol.length()-4);
        return new String[]{symbol.substring(0, mid), symbol.substring(mid)};
    }

    private static String fmtPrice(BigDecimal v) {
        return new DecimalFormat("#,##0.########").format(v);
    }
    private static String fmtQty(BigDecimal v) {
        return new DecimalFormat("#,##0.########").format(v);
    }
}
