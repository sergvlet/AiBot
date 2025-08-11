package com.chicu.aibot.bot.menu.feature.ai.strategy.scalping.view;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LiveSnapshot {
    // тикер/цена
    String priceStr;   // например "2.3456"
    String changeStr;  // например "▲ +1.23%"

    // баланс
    String base;       // BTC
    String quote;      // USDT
    String baseBal;    // "0.12345"
    String quoteBal;   // "125.50"

    // открытые ордера
    int openCount;
    String openOrdersBlock; // многострочный текст со списком

    // для блоков расчётов
    double lastPrice;     // текущее числовое значение (для PnL)
}
