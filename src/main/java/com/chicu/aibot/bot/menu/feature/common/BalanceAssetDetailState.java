package com.chicu.aibot.bot.menu.feature.common;

import com.chicu.aibot.bot.menu.core.MenuState;
import com.chicu.aibot.exchange.client.ExchangeClient;
import com.chicu.aibot.exchange.client.ExchangeClientFactory;
import com.chicu.aibot.exchange.model.AccountInfo;
import com.chicu.aibot.exchange.model.BalanceInfo;
import com.chicu.aibot.exchange.model.TickerInfo;
import com.chicu.aibot.exchange.service.ExchangeSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component("balance_asset_detail")
@RequiredArgsConstructor
public class BalanceAssetDetailState implements MenuState {

    private final ExchangeSettingsService settingsService;
    private final ExchangeClientFactory clientFactory;

    @Setter
    private String currentAsset; // выбранная монета

    @Override
    public String name() {
        return "balance_asset_detail";
    }

    @Override
    public SendMessage render(Long chatId) {
        if (currentAsset == null) {
            return SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("❌ Не указана монета")
                    .replyMarkup(InlineKeyboardMarkup.builder()
                            .keyboard(List.of(List.of(
                                    InlineKeyboardButton.builder()
                                            .text("⬅️ Назад к списку")
                                            .callbackData("balance_assets")
                                            .build()
                            ))).build())
                    .build();
        }
        return buildMessage(chatId, currentAsset);
    }

    @Override
    public String handleInput(Update update) {
        if (!update.hasCallbackQuery()) return "balance_menu";

        String data = update.getCallbackQuery().getData();

        if ("balance_assets".equals(data)) {
            this.currentAsset = null;
            return "balance_assets";
        }

        return name();
    }

    private SendMessage buildMessage(Long chatId, String asset) {
        var settings = settingsService.getOrCreate(chatId);
        var keys = settingsService.getApiKey(chatId);
        ExchangeClient client = clientFactory.getClient(settings.getExchange());

        Optional<BalanceInfo> balanceOpt = Optional.empty();
        try {
            AccountInfo acc = client.fetchAccountInfo(keys.getPublicKey(), keys.getSecretKey(), settings.getNetwork());
            balanceOpt = acc.getBalances().stream()
                    .filter(b -> asset.equalsIgnoreCase(b.getAsset()))
                    .findFirst();
        } catch (Exception e) {
            log.error("Ошибка загрузки баланса {}: {}", asset, e.getMessage());
        }

        String text;
        if (balanceOpt.isPresent()) {
            BalanceInfo b = balanceOpt.get();
            BigDecimal free = b.getFree().setScale(8, RoundingMode.HALF_UP).stripTrailingZeros();
            BigDecimal locked = b.getLocked().setScale(8, RoundingMode.HALF_UP).stripTrailingZeros();
            BigDecimal total = free.add(locked);

            BigDecimal usdValue = BigDecimal.ZERO;
            if (!"USDT".equalsIgnoreCase(asset)) {
                try {
                    String symbol = asset + "USDT";
                    Optional<TickerInfo> ticker = client.getTicker(symbol, settings.getNetwork());
                    if (ticker.isPresent()) {
                        usdValue = total.multiply(ticker.get().getPrice()).setScale(2, RoundingMode.HALF_UP);
                    }
                } catch (Exception e) {
                    log.warn("Не удалось получить цену {} в USDT: {}", asset, e.getMessage());
                }
            } else {
                usdValue = total.setScale(2, RoundingMode.HALF_UP);
            }

            text = String.format(
                    """
                    💰 *Баланс монеты %s*
                    
                    Свободно: `%s`
                    Заблокировано: `%s`
                    Всего: `%s`
                    
                    💵 ~ Стоимость в USDT: *%s*""",
                    b.getAsset(),
                    free.toPlainString(),
                    locked.toPlainString(),
                    total.toPlainString(),
                    usdValue.toPlainString()
            );
        } else {
            text = "❌ Баланс для монеты `" + asset + "` не найден";
        }

        return SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode("Markdown")
                .replyMarkup(InlineKeyboardMarkup.builder()
                        .keyboard(List.of(List.of(
                                InlineKeyboardButton.builder()
                                        .text("⬅️ Назад к списку")
                                        .callbackData("balance_assets")
                                        .build()
                        ))).build())
                .build();
    }
}
