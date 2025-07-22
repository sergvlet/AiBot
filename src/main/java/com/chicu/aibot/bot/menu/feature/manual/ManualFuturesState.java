package com.chicu.aibot.bot.menu.feature.manual;

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
public class ManualFuturesState implements MenuState {

    private final InlineKeyboardMarkup keyboard;

    public ManualFuturesState() {
        this.keyboard = InlineKeyboardMarkup.builder()
            .keyboard(List.of(
                // 1. Пары
                List.of(
                    InlineKeyboardButton.builder()
                        .text("BTC/USDT")
                        .callbackData("futures_pair_btc_usdt")
                        .build(),
                    InlineKeyboardButton.builder()
                        .text("ETH/USDT")
                        .callbackData("futures_pair_eth_usdt")
                        .build(),
                    InlineKeyboardButton.builder()
                        .text("SOL/USDT")
                        .callbackData("futures_pair_sol_usdt")
                        .build()
                ),
                // 2. Тип ордера
                List.of(
                    InlineKeyboardButton.builder()
                        .text("📈 Market")
                        .callbackData("futures_order_market")
                        .build(),
                    InlineKeyboardButton.builder()
                        .text("📉 Limit")
                        .callbackData("futures_order_limit")
                        .build()
                ),
                // 3. Действие: Long / Short
                List.of(
                    InlineKeyboardButton.builder()
                        .text("🟢 Long")
                        .callbackData("futures_action_long")
                        .build(),
                    InlineKeyboardButton.builder()
                        .text("🔴 Short")
                        .callbackData("futures_action_short")
                        .build()
                ),
                // 4. Назад
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
        return "manual_futures";
    }

    @Override
    public SendMessage render(Long chatId) {
        log.info("Рендер Futures-подменю для chatId={}", chatId);
        String text = "*📈 Futures Trading* — торговля вечными контрактами с кредитным плечом.\n\n" +
                      "1️⃣ Выберите торговую пару\n" +
                      "2️⃣ Выберите тип ордера (Market / Limit)\n" +
                      "3️⃣ Выберите позицию: *Long* или *Short*";
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
            log.info("ManualFuturesState: нажата кнопка '{}'", data);
            return switch (data) {
                case "futures_pair_btc_usdt"   -> "futures_pair_btc_usdt";
                case "futures_pair_eth_usdt"   -> "futures_pair_eth_usdt";
                case "futures_pair_sol_usdt"   -> "futures_pair_sol_usdt";
                case "futures_order_market"    -> "futures_order_market";
                case "futures_order_limit"     -> "futures_order_limit";
                case "futures_action_long"     -> "futures_action_long";
                case "futures_action_short"    -> "futures_action_short";
                case "manual_trading_settings" -> "manual_trading_settings";
                default                        -> {
                    log.warn("Неизвестный callback '{}' в Futures-подменю", data);
                    yield name();
                }
            };
        }
        return name();
    }
}
