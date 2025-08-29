package com.chicu.aibot.bot.menu.feature.common;

import com.chicu.aibot.bot.menu.core.MenuService;
import com.chicu.aibot.bot.menu.core.MenuState;
import com.chicu.aibot.exchange.enums.NetworkType;
import com.chicu.aibot.exchange.client.ExchangeClient;
import com.chicu.aibot.exchange.client.ExchangeClientFactory;
import com.chicu.aibot.exchange.service.ExchangeSettingsService;
import com.chicu.aibot.exchange.model.AccountInfo;
import com.chicu.aibot.exchange.model.BalanceInfo;
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
@Component("balance_menu")
@RequiredArgsConstructor
public class BalanceMenuState implements MenuState {

    private final ExchangeSettingsService settingsService;
    private final ExchangeClientFactory clientFactory;

    @Override
    public String name() {
        return "balance_menu";
    }

    @Override
    public SendMessage render(Long chatId) {
        log.info("Рендер меню управления балансом для chatId={}", chatId);

        // Загружаем текущие настройки
        var settings = settingsService.getOrCreate(chatId);
        var keys = settingsService.getApiKey(chatId);

        String exchange = settings.getExchange().name();
        NetworkType network = settings.getNetwork();

        ExchangeClient client = clientFactory.getClient(settings.getExchange());

        StringBuilder text = new StringBuilder("💰 *Управление балансом*\n\n");

        try {
            AccountInfo acc = client.fetchAccountInfo(keys.getPublicKey(), keys.getSecretKey(), network);

            BigDecimal total = BigDecimal.ZERO;
            for (BalanceInfo b : acc.getBalances()) {
                if (b.getFree().compareTo(BigDecimal.ZERO) > 0 || b.getLocked().compareTo(BigDecimal.ZERO) > 0) {
                    total = total.add(b.getFree()).add(b.getLocked());
                }
            }
            text.append("Биржа: *").append(exchange).append("*\n");
            text.append("Сеть: *").append(network).append("*\n\n");
            text.append("Итого активов: ").append(total.toPlainString()).append("\n");

        } catch (Exception e) {
            text.append("⚠️ Не удалось получить баланс: ").append(e.getMessage());
        }

        // Клавиатура
        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboard(List.of(
                        List.of(InlineKeyboardButton.builder()
                                .text("🌐 Обзор по биржам")
                                .callbackData("balance_overview")
                                .build()),
                        List.of(InlineKeyboardButton.builder()
                                .text("📊 Баланс по монетам")
                                .callbackData("balance_assets")
                                .build()),
                        List.of(InlineKeyboardButton.builder()
                                .text("📉 Скрыть мелочь")
                                .callbackData("balance_hide_dust")
                                .build()),
                        List.of(InlineKeyboardButton.builder()
                                .text("⬅️ Назад")
                                .callbackData(MenuService.MAIN_MENU) // возвращаем в главное меню
                                .build())
                ))
                .build();

        return SendMessage.builder()
                .chatId(chatId.toString())
                .text(text.toString())
                .parseMode("Markdown")
                .replyMarkup(keyboard)
                .build();
    }

    @Override
    public String handleInput(Update update) {
        if (!update.hasCallbackQuery()) return name();

        String data = update.getCallbackQuery().getData();
        log.info("BalanceMenuState: нажата кнопка '{}'", data);

        return switch (data) {
            case "balance_overview" -> "balance_overview"; // отдельное состояние с суммой по биржам
            case "balance_assets"   -> "balance_assets";   // состояние со списком монет
            case "balance_hide_dust"-> "balance_hide_dust"; // фильтр мелочи
            case MenuService.MAIN_MENU -> MenuService.MAIN_MENU;
            default -> name();
        };
    }
}
