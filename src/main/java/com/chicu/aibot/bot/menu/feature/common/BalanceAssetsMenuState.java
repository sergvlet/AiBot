package com.chicu.aibot.bot.menu.feature.common;

import com.chicu.aibot.bot.menu.core.MenuState;
import com.chicu.aibot.exchange.client.ExchangeClient;
import com.chicu.aibot.exchange.client.ExchangeClientFactory;
import com.chicu.aibot.exchange.model.AccountInfo;
import com.chicu.aibot.exchange.model.BalanceInfo;
import com.chicu.aibot.exchange.service.ExchangeSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component("balance_assets_menu")
@RequiredArgsConstructor
public class BalanceAssetsMenuState implements MenuState {

    private final ExchangeSettingsService settingsService;
    private final ExchangeClientFactory clientFactory;

    @Override
    public String name() {
        return "balance_assets_menu";
    }

    @Override
    public SendMessage render(Long chatId) {
        log.info("Рендер подменю баланса по монетам для chatId={}", chatId);

        var settings = settingsService.getOrCreate(chatId);
        var keys = settingsService.getApiKey(chatId);

        ExchangeClient client = clientFactory.getClient(settings.getExchange());

        StringBuilder text = new StringBuilder("📊 *Выберите монету для просмотра баланса:*\n\n");

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        try {
            AccountInfo acc = client.fetchAccountInfo(keys.getPublicKey(), keys.getSecretKey(), settings.getNetwork());

            int col = 0;
            List<InlineKeyboardButton> currentRow = new ArrayList<>();

            for (BalanceInfo b : acc.getBalances()) {
                if (b.getFree().signum() > 0 || b.getLocked().signum() > 0) {
                    InlineKeyboardButton btn = InlineKeyboardButton.builder()
                            .text(b.getAsset())
                            .callbackData("balance_asset_" + b.getAsset())
                            .build();
                    currentRow.add(btn);
                    col++;

                    if (col == 3) { // 3 кнопки в ряд
                        rows.add(currentRow);
                        currentRow = new ArrayList<>();
                        col = 0;
                    }
                }
            }
            if (!currentRow.isEmpty()) {
                rows.add(currentRow);
            }

        } catch (Exception e) {
            text.append("⚠️ Ошибка получения балансов: ").append(e.getMessage());
        }

        // кнопка "Назад"
        rows.add(List.of(InlineKeyboardButton.builder()
                .text("⬅️ Назад")
                .callbackData("balance_menu")
                .build()));

        InlineKeyboardMarkup kb = InlineKeyboardMarkup.builder().keyboard(rows).build();

        return SendMessage.builder()
                .chatId(chatId.toString())
                .text(text.toString())
                .parseMode("Markdown")
                .replyMarkup(kb)
                .build();
    }

    @Override
    public String handleInput(Update update) {
        if (!update.hasCallbackQuery()) return name();

        String data = update.getCallbackQuery().getData();
        if (data.startsWith("balance_asset_")) {
            return "balance_asset_detail:" + data.substring("balance_asset_".length());
        }
        return "balance_menu";
    }
}
