package com.chicu.aibot.bot.menu.feature.ai.strategy.scalping.service.impl;

import com.chicu.aibot.bot.menu.feature.ai.strategy.scalping.service.ScalpingLiveService;
import com.chicu.aibot.bot.menu.feature.ai.strategy.scalping.service.ScalpingPanelRenderer;
import com.chicu.aibot.bot.menu.feature.ai.strategy.scalping.view.LiveSnapshot;
import com.chicu.aibot.strategy.scalping.model.ScalpingStrategySettings;
import com.chicu.aibot.strategy.scalping.service.ScalpingStrategySettingsService;
import com.chicu.aibot.trading.trade.TradeLogEvent;
import com.chicu.aibot.trading.trade.TradeLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ScalpingPanelRendererImpl implements ScalpingPanelRenderer {

    public static final String NAME = "ai_trading_scalping_config";
    public static final String BTN_REFRESH     = "scalp_refresh";
    public static final String BTN_EDIT_SYMBOL = "edit_symbol";
    public static final String BTN_TOGGLE      = "scalp_toggle_active";

    private final ScalpingStrategySettingsService settingsService;
    private final ScalpingLiveService liveService;
    private final TradeLogService tradeLogService;

    @Override
    public SendMessage render(Long chatId) {
        ScalpingStrategySettings s = settingsService.getOrCreate(chatId);
        String symbol = nvl(s.getSymbol());

        LiveSnapshot live = liveService.build(chatId, symbol);
        String pnlBlock = buildPnlBlock(chatId, symbol, live);

        String text = """
                *📊 Scalping Strategy*
                %s

                *Рынок:* `%s`
                Изм.: %s  |  Цена: `%s`

                *Баланс:*
                • %s: `%s`
                • %s: `%s`

                *Сделки / PnL:*
                %s

                *Открытые ордера (%d):*
                %s

                *Параметры:*
                • Объем: `%.4f`
                • Таймфрейм: `%s`
                • История: `%d` • Окно: `%d`
                • ΔЦены: `%.2f%%` • Макс. спред: `%.2f%%`
                • TP: `%.2f%%` • SL: `%.2f%%`
                • Статус: *%s*
                """.formatted(
                s.isActive() ? "Стратегия: 🟢 *Запущена*" : "Стратегия: 🔴 *Остановлена*",

                symbol,
                live.getChangeStr(),             // сначала изменение
                live.getPriceStr(),              // затем цена

                live.getBase(),  live.getBaseBal(),
                live.getQuote(), live.getQuoteBal(),

                pnlBlock,

                live.getOpenCount(),
                (live.getOpenOrdersBlock() == null || live.getOpenOrdersBlock().isBlank())
                        ? "_нет_" : live.getOpenOrdersBlock(),

                s.getOrderVolume(),
                s.getTimeframe(),
                s.getCachedCandlesLimit(),
                s.getWindowSize(),
                s.getPriceChangeThreshold(),
                s.getSpreadThreshold(),
                s.getTakeProfitPct(),
                s.getStopLossPct(),
                s.isActive() ? "🟢 Запущена" : "🔴 Остановлена"
        );

        List<List<InlineKeyboardButton>> rows = new ArrayList<>(List.of(
                List.of(button("⟳ Обновить", BTN_REFRESH), button("‹ Назад", "ai_trading")),
                List.of(button("✏️ Пара", BTN_EDIT_SYMBOL), button("✏️ Объем", "scalp_edit_orderVolume"), button("✏️ История", "scalp_edit_cachedCandlesLimit")),
                List.of(button("✏️ Окно", "scalp_edit_windowSize"), button("✏️ ΔЦены", "scalp_edit_priceChangeThreshold"), button("✏️ Макс. спред", "scalp_edit_spreadThreshold")),
                List.of(button("✏️ TP", "scalp_edit_takeProfitPct"), button("✏️ SL", "scalp_edit_stopLossPct")),
                List.of(button(s.isActive() ? "🛑 Остановить стратегию" : "▶️ Запустить стратегию", BTN_TOGGLE))
        ));

        return SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode("Markdown")
                .disableWebPagePreview(true)
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                .build();
    }

    private String buildPnlBlock(Long chatId, String symbol, LiveSnapshot live) {
        TradeLogEvent last = tradeLogService.getLastTrade(chatId, symbol).orElse(null);
        if (last == null) return "_нет сделок_";

        double entry = last.getPrice();
        double qty   = last.getQuantity();
        double now   = live.getLastPrice();

        double pnlAbs;      // в quote
        double pnlPct;      // %
        String dirEmoji;

        switch (last.getSide()) {
            case BUY -> {
                pnlAbs = (now - entry) * qty;
                pnlPct = entry > 0 ? (now - entry) / entry * 100.0 : 0.0;
            }
            case SELL -> {
                pnlAbs = (entry - now) * qty;
                pnlPct = entry > 0 ? (entry - now) / entry * 100.0 : 0.0;
            }
            default -> {
                pnlAbs = 0.0;
                pnlPct = 0.0;
            }
        }
        dirEmoji = pnlAbs >= 0 ? "🟢" : "🔴";

        String pnlAbsS = formatMoney(pnlAbs, live.getQuote());
        String pnlPctS = String.format("%s%.2f%%", (pnlPct >= 0 ? "+" : ""), pnlPct);
        String investedS = formatMoney(entry * qty, live.getQuote());

        return """
               • Последняя: *%s* %s @`%.8f`  qty `%.6f`
               • Вложено: `%s`
               • PnL: %s `%s`  (%s)
               """.stripTrailing().formatted(
                last.getSide().name(), symbol, entry, qty,
                investedS,
                dirEmoji, pnlAbsS, pnlPctS
        );
    }

    private static String formatMoney(double v, String quote) {
        String s = String.format("%,.2f", Math.abs(v));
        return (v < 0 ? "-" : "+") + s + " " + quote;
    }

    private static String nvl(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }

    private InlineKeyboardButton button(String text, String data) {
        return InlineKeyboardButton.builder().text(text).callbackData(data).build();
    }
}
