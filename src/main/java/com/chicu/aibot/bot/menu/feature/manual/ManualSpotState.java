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
public class ManualSpotState implements MenuState {

    private final InlineKeyboardMarkup keyboard;

    public ManualSpotState() {
        this.keyboard = InlineKeyboardMarkup.builder()
            .keyboard(List.of(
                List.of(
                    InlineKeyboardButton.builder()
                        .text("BTC/USDT")
                        .callbackData("spot_pair_btc_usdt")
                        .build(),
                    InlineKeyboardButton.builder()
                        .text("ETH/USDT")
                        .callbackData("spot_pair_eth_usdt")
                        .build(),
                    InlineKeyboardButton.builder()
                        .text("ADA/USDT")
                        .callbackData("spot_pair_ada_usdt")
                        .build()
                ),
                List.of(
                    InlineKeyboardButton.builder()
                        .text("📈 Market")
                        .callbackData("spot_order_market")
                        .build(),
                    InlineKeyboardButton.builder()
                        .text("📉 Limit")
                        .callbackData("spot_order_limit")
                        .build()
                ),
                List.of(
                    InlineKeyboardButton.builder()
                        .text("🟢 Buy")
                        .callbackData("spot_action_buy")
                        .build(),
                    InlineKeyboardButton.builder()
                        .text("🔴 Sell")
                        .callbackData("spot_action_sell")
                        .build()
                ),
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
        return "manual_spot";
    }

    @Override
    public SendMessage render(Long chatId) {
        log.info("Рендер Spot-подменю для chatId={}", chatId);
        String text = """
                *🔄 Spot Trading* — это классическая торговля по текущим рыночным ценам, \
                когда вы сразу покупаете или продаёте актив с расчётом на моментальное исполнение.
                
                1️⃣ Выберите торговую пару
                2️⃣ Выберите тип ордера (Market / Limit)
                3️⃣ Укажите, хотите *Buy* или *Sell*""";
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
            log.info("ManualSpotState: нажата кнопка '{}'", data);
            // здесь вы обычно сохраняете выбор и переходите дальше
            return switch (data) {
                case "spot_pair_btc_usdt"     -> "spot_pair_btc_usdt";
                case "spot_pair_eth_usdt"     -> "spot_pair_eth_usdt";
                case "spot_pair_ada_usdt"     -> "spot_pair_ada_usdt";
                case "spot_order_market"      -> "spot_order_market";
                case "spot_order_limit"       -> "spot_order_limit";
                case "spot_action_buy"        -> "spot_action_buy";
                case "spot_action_sell"       -> "spot_action_sell";
                case "manual_trading_settings"-> "manual_trading_settings";
                default                        -> {
                    log.warn("Неизвестный callback '{}' в Spot-подменю, остаёмся", data);
                    yield name();
                }
            };
        }
        // любое другое сообщение — остаёмся
        return name();
    }
}
