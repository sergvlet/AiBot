package com.chicu.aibot.bot.menu.feature.common;

import com.chicu.aibot.bot.menu.core.MenuState;
import com.chicu.aibot.exchange.client.ExchangeClient;
import com.chicu.aibot.exchange.client.ExchangeClientFactory;
import com.chicu.aibot.exchange.enums.Exchange;
import com.chicu.aibot.exchange.enums.NetworkType;
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

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component("balance_overview")
@RequiredArgsConstructor
public class BalanceOverviewState implements MenuState {

    private final ExchangeSettingsService settingsService;
    private final ExchangeClientFactory clientFactory;

    @Override
    public String name() {
        return "balance_overview";
    }

    @Override
    public SendMessage render(Long chatId) {
        log.info("Рендер обзора по биржам для chatId={}", chatId);

        StringBuilder text = new StringBuilder("🌐 *Обзор по биржам*\n\n");

        for (Exchange ex : Exchange.values()) {
            try {
                var settings = settingsService.getOrCreate(chatId);
                var keys = settingsService.getApiKey(chatId);
                NetworkType network = settings.getNetwork();

                ExchangeClient client = clientFactory.getClient(ex);
                AccountInfo acc = client.fetchAccountInfo(keys.getPublicKey(), keys.getSecretKey(), network);

                BigDecimal total = BigDecimal.ZERO;
                for (BalanceInfo b : acc.getBalances()) {
                    total = total.add(b.getFree()).add(b.getLocked());
                }
                text.append("Биржа: *").append(ex).append("*  →  ")
                        .append(total.toPlainString()).append("\n");
            } catch (Exception e) {
                text.append("Биржа: *").append(ex).append("*  ⚠️ ошибка: ").append(e.getMessage()).append("\n");
            }
        }

        InlineKeyboardMarkup kb = InlineKeyboardMarkup.builder()
                .keyboard(List.of(
                        List.of(InlineKeyboardButton.builder()
                                .text("⬅️ Назад")
                                .callbackData("balance_menu")
                                .build())
                ))
                .build();

        return SendMessage.builder()
                .chatId(chatId.toString())
                .text(text.toString())
                .parseMode("Markdown")
                .replyMarkup(kb)
                .build();
    }

    @Override
    public String handleInput(Update update) {
        return "balance_menu";
    }
}
