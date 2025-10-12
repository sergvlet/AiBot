package com.chicu.aibot.bot.menu.feature.ai.strategy.fibonacci.service.impl;

import com.chicu.aibot.bot.menu.feature.ai.strategy.fibonacci.service.FibonacciGridPanelRenderer;
import com.chicu.aibot.bot.menu.feature.ai.strategy.view.LiveSnapshot;
import com.chicu.aibot.bot.ui.AdaptiveKeyboard;
import com.chicu.aibot.exchange.order.model.ExchangeOrderEntity;
import com.chicu.aibot.exchange.order.service.ExchangeOrderDbService;
import com.chicu.aibot.exchange.service.MarketLiveService;
import com.chicu.aibot.strategy.fibonacci.model.FibonacciGridStrategySettings;
import com.chicu.aibot.strategy.fibonacci.service.FibonacciGridStrategySettingsService;
import com.chicu.aibot.trading.trade.TradeLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import static com.chicu.aibot.bot.menu.feature.ai.strategy.view.PanelTextUtils.boolEmoji;
import static com.chicu.aibot.bot.menu.feature.ai.strategy.view.PanelTextUtils.nvl;

@Component
@RequiredArgsConstructor
public class FibonacciGridPanelRendererImpl implements FibonacciGridPanelRenderer {

    public static final String NAME               = "ai_trading_fibonacci_config";
    public static final String BTN_REFRESH        = "fib_refresh";
    public static final String BTN_EDIT_SYMBOL    = "fib_edit_symbol";
    public static final String BTN_TOGGLE_ACTIVE  = "fib_toggle_active";
    public static final String BTN_TOGGLE_LONG    = "fib_toggle_allow_long";
    public static final String BTN_TOGGLE_SHORT   = "fib_toggle_allow_short";
    public static final String BTN_EDIT_ORDER_VOL = "fib_edit_orderVolume";
    public static final String BTN_EDIT_GRID      = "fib_edit_gridSizePct";
    public static final String BTN_EDIT_MAX_ORD   = "fib_edit_maxActiveOrders";
    public static final String BTN_EDIT_TP        = "fib_edit_takeProfitPct";
    public static final String BTN_EDIT_SL        = "fib_edit_stopLossPct";
    public static final String BTN_EDIT_TF        = "fib_edit_timeframe";
    public static final String BTN_HELP           = "fib_help";

    private final FibonacciGridStrategySettingsService settingsService;
    private final MarketLiveService liveService;
    private final TradeLogService tradeLogService;
    private final ExchangeOrderDbService orderDb;

    @Override
    public SendMessage render(Long chatId) {
        FibonacciGridStrategySettings s = settingsService.getOrCreate(chatId);
        String symbol = nvl(s.getSymbol());
        LiveSnapshot live = liveService.build(chatId, symbol);

        // Total PnL (из TradeLogService), безопасное форматирование
        String totalPnlBlock = tradeLogService.getTotalPnl(chatId, symbol)
                .map(this::formatSignedPercent)
                .map(v -> "*Итоговый PnL:* `" + v + "`")
                .orElse("_нет данных по PnL_");

        // Последняя сделка — показываем просто как «Есть/Нет», чтобы не зависеть от полей TradeLogEntry
        String lastTradeBlock = tradeLogService.getLastTrade(chatId, symbol).isPresent()
                ? "последняя сделка: `есть`"
                : "_нет сделок_";

        List<ExchangeOrderEntity> openOrders = orderDb.findOpenByChatAndSymbol(chatId, symbol);
        String openOrdersBlock = formatOpenOrdersBlock(openOrders);

        String text = ("""
                *📊 Fibonacci Grid Strategy*
                %s

                *Рынок:* `%s`
                %s Изм.: %s | 💵 Цена: `%s`

                *Баланс:*
                • %s: `%s`
                • %s: `%s`

                *Сделки / PnL:*
                %s
                %s

                *Открытые ордера (%d):*
                %s

                *Параметры:*
                • Объем: `%.4f`
                • Таймфрейм: `%s`
                • История: `%d`
                • Шаг сетки: `%.4f%%`
                • Макс. активных ордеров: `%d`
                • LONG: %s • SHORT: %s
                • TP: `%.2f%%` • SL: `%.2f%%`
                • Статус: *%s*
                """).formatted(
                s.isActive() ? "🟢 *Запущена*" : "🔴 *Остановлена*",
                symbol,
                live.getChangePct() >= 0 ? "📈" : "📉",
                live.getChangeStr(),
                live.getPriceStr(),
                live.getBase(), live.getBaseBal(),
                live.getQuote(), live.getQuoteBal(),
                lastTradeBlock,
                totalPnlBlock,
                openOrders.size(),
                openOrdersBlock,
                s.getOrderVolume(),
                s.getTimeframe(),
                s.getCachedCandlesLimit(),
                s.getGridSizePct(),
                s.getMaxActiveOrders(),
                boolEmoji(s.getAllowLong()), boolEmoji(s.getAllowShort()),
                s.getTakeProfitPct(),
                s.getStopLossPct(),
                s.isActive() ? "🟢 Запущена" : "🔴 Остановлена"
        ).stripTrailing();

        InlineKeyboardMarkup markup = AdaptiveKeyboard.markupFromGroups(List.of(
                List.of(
                        AdaptiveKeyboard.btn("ℹ️ Описание", BTN_HELP),
                        AdaptiveKeyboard.btn("⏱ Обновить", BTN_REFRESH),
                        AdaptiveKeyboard.btn("‹ Назад", "ai_trading")
                ),
                List.of(
                        AdaptiveKeyboard.btn("🎯 Символ", BTN_EDIT_SYMBOL),
                        AdaptiveKeyboard.btn("💰 Объём %", BTN_EDIT_ORDER_VOL),
                        AdaptiveKeyboard.btn("🧱 Шаг %", BTN_EDIT_GRID),
                        AdaptiveKeyboard.btn("📊 Макс. орд.", BTN_EDIT_MAX_ORD)
                ),
                List.of(
                        AdaptiveKeyboard.btn("📈 LONG " + onOff(s.getAllowLong()), BTN_TOGGLE_LONG),
                        AdaptiveKeyboard.btn("📉 SHORT " + onOff(s.getAllowShort()), BTN_TOGGLE_SHORT),
                        AdaptiveKeyboard.btn("🎯 TP %", BTN_EDIT_TP),
                        AdaptiveKeyboard.btn("🛡 SL %", BTN_EDIT_SL),
                        AdaptiveKeyboard.btn("⏱ Таймфрейм", BTN_EDIT_TF)
                ),
                List.of(
                        AdaptiveKeyboard.btn(s.isActive() ? "🔴 Остановить" : "🟢 Запустить", BTN_TOGGLE_ACTIVE)
                )
        ), 3);

        return SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode("Markdown")
                .disableWebPagePreview(true)
                .replyMarkup(markup)
                .build();
    }

    private String onOff(Boolean b) {
        return Boolean.TRUE.equals(b) ? "ON" : "OFF";
    }

    private String formatSignedPercent(double v) {
        // Локаль RU для разделителей, но без deprecated конструкторов
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU"));
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        String num = nf.format(Math.abs(v));
        return (v >= 0 ? "+" : "−") + num + "%";
    }

    private String formatOpenOrdersBlock(List<ExchangeOrderEntity> list) {
        if (list == null || list.isEmpty()) return "_нет ордеров_";
        StringBuilder sb = new StringBuilder();
        for (ExchangeOrderEntity o : list) {
            sb.append("• ")
              .append(o.getSide()).append(" ").append(o.getType())
              .append(" qty `").append(nvl(String.valueOf(o.getQuantity()))).append('`')
              .append(" @ `").append(nvl(String.valueOf(o.getPrice()))).append('`')
              .append(" filled `").append(nvl(String.valueOf(o.getExecutedQty()))).append('`')
              .append(" *").append(o.getStatus()).append("* ")
              .append("(#").append(nvl(o.getOrderId())).append(")\n");
        }
        return sb.toString().stripTrailing();
    }
}
