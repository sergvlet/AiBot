package com.chicu.aibot.bot.menu.feature.manual;

import com.chicu.aibot.bot.menu.core.MenuService;
import com.chicu.aibot.bot.menu.core.MenuState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

@Slf4j
@Component
public class ManualMarginState implements MenuState {

    private final InlineKeyboardMarkup keyboard;

    public ManualMarginState() {
        this.keyboard = InlineKeyboardMarkup.builder()
            .keyboard(List.of(
                // 1. Пары
                List.of(
                    InlineKeyboardButton.builder()
                        .text("BTC/USDT")
                        .callbackData("margin_pair_btc_usdt")
                        .build(),
                    InlineKeyboardButton.builder()
                        .text("ETH/USDT")
                        .callbackData("margin_pair_eth_usdt")
                        .build()
                ),
                // 2. Кредитное плечо
                List.of(
                    InlineKeyboardButton.builder()
                        .text("2×")
                        .callbackData("margin_leverage_2x")
                        .build(),
                    InlineKeyboardButton.builder()
                        .text("5×")
                        .callbackData("margin_leverage_5x")
                        .build(),
                    InlineKeyboardButton.builder()
                        .text("10×")
                        .callbackData("margin_leverage_10x")
                        .build()
                ),
                // 3. Тип ордера
                List.of(
                    InlineKeyboardButton.builder()
                        .text("📈 Market")
                        .callbackData("margin_order_market")
                        .build(),
                    InlineKeyboardButton.builder()
                        .text("📉 Limit")
                        .callbackData("margin_order_limit")
                        .build()
                ),
                // 4. Buy / Sell
                List.of(
                    InlineKeyboardButton.builder()
                        .text("🟢 Buy")
                        .callbackData("margin_action_buy")
                        .build(),
                    InlineKeyboardButton.builder()
                        .text("🔴 Sell")
                        .callbackData("margin_action_sell")
                        .build()
                ),
                // 5. Назад
                List.of(
                    InlineKeyboardButton.builder()
                        .text("⬅️ Назад")
                        .callbackData("manual_trading_settings")
                        .build()
                )
            ))
            .build();
    }

    @Override
    public String name() {
        return "manual_margin";
    }

    @Override
    public SendMessage render(Long chatId) {
        log.info("Рендер Margin-подменю для chatId={}", chatId);
        String text = "*⚖️ Margin Trading* — торговля с заемными средствами:\n\n" +
                      "1️⃣ Выберите торговую пару\n" +
                      "2️⃣ Выберите кредитное плечо\n" +
                      "3️⃣ Выберите тип ордера (Market / Limit)\n" +
                      "4️⃣ Укажите Buy или Sell";
        return SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode("Markdown")
                .replyMarkup(keyboard)
                .build();
    }

    @Override
    public String handleInput(Update update) {
        if (update.hasCallbackQuery()) {
            String data = update.getCallbackQuery().getData();
            log.info("ManualMarginState: нажата кнопка '{}'", data);
            return switch (data) {
                case "margin_pair_btc_usdt"      -> "margin_pair_btc_usdt";
                case "margin_pair_eth_usdt"      -> "margin_pair_eth_usdt";
                case "margin_leverage_2x"        -> "margin_leverage_2x";
                case "margin_leverage_5x"        -> "margin_leverage_5x";
                case "margin_leverage_10x"       -> "margin_leverage_10x";
                case "margin_order_market"       -> "margin_order_market";
                case "margin_order_limit"        -> "margin_order_limit";
                case "margin_action_buy"         -> "margin_action_buy";
                case "margin_action_sell"        -> "margin_action_sell";
                case "manual_trading_settings"   -> "manual_trading_settings";
                default                          -> {
                    log.warn("Неизвестный callback '{}' в Margin-подменю", data);
                    yield name();
                }
            };
        }
        return name();
    }
}
