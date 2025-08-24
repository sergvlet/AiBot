package com.chicu.aibot.bot.menu.feature.ai.strategy.view;

import lombok.Builder;
import lombok.Value;

import java.util.Locale;

@Value
@Builder
public class LiveSnapshot {
    // Тикер/цена
    String priceStr;     // например "2,3456" (готовая строка для вывода)
    double changePct;    // изменение в процентах, например +1.23 / -0.87

    // Баланс
    String base;         // BTC
    String quote;        // USDT
    String baseBal;      // "0.12345"
    String quoteBal;     // "125.50"

    // Открытые ордера
    int openCount;
    String openOrdersBlock; // многострочный текст со списком

    // Для блоков расчётов
    double lastPrice;    // текущее числовое значение (для PnL)

    /**
     * Человеко читаемая строка изменения цены:
     * "▲ +1,23%" / "▼ -0,87%" / "• 0,00%".
     */
    public String getChangeStr() {
        double pct = this.changePct;

        if (Double.isNaN(pct)) {
            return "—";
        }

        String arrow = pct > 0 ? "▲" : (pct < 0 ? "▼" : "•");
        String sign  = pct > 0 ? "+" : (pct < 0 ? "-" : "");
        String num   = String.format(Locale.forLanguageTag("ru-RU"), "%,.2f", Math.abs(pct));

        return arrow + " " + sign + num + "%";
    }
}
