package com.chicu.aibot.bot.menu.feature.common;

import com.chicu.aibot.bot.menu.core.MenuState;
import com.chicu.aibot.exchange.client.ExchangeClient;
import com.chicu.aibot.exchange.client.ExchangeClientFactory;
import com.chicu.aibot.exchange.model.AccountInfo;
import com.chicu.aibot.exchange.model.BalanceInfo;
import com.chicu.aibot.exchange.model.TickerInfo;
import com.chicu.aibot.exchange.service.ExchangeSettingsService;
import com.chicu.aibot.strategy.model.Order;
import com.chicu.aibot.strategy.service.OrderService;
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
import java.util.*;

@Slf4j
@Component("balance_asset_detail")
@RequiredArgsConstructor
public class BalanceAssetDetailState implements MenuState {

    private final ExchangeSettingsService settingsService;
    private final ExchangeClientFactory clientFactory;
    private final OrderService orderService;

    @Setter
    private String currentAsset; // выбранная монета

    /** Ожидаемые действия (SELL или BUY) по чатам */
    private final Map<Long, String> pendingAction = new HashMap<>();

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
        return buildMessage(chatId, currentAsset, null);
    }

    @Override
    public String handleInput(Update update) {
        if (!update.hasCallbackQuery()) return "balance_menu";

        String data = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();

        switch (data) {
            case "balance_assets" -> {
                this.currentAsset = null;
                return "balance_assets";
            }
            case "convert_to_usdt" -> {
                pendingAction.put(chatId, "SELL");
                return name();
            }
            case "buy_with_usdt" -> {
                pendingAction.put(chatId, "BUY");
                return name();
            }
            case "confirm_yes" -> {
                String action = pendingAction.remove(chatId);
                if ("SELL".equals(action)) return executeSell(chatId);
                if ("BUY".equals(action)) return executeBuy(chatId);
            }
            case "confirm_no" -> {
                pendingAction.remove(chatId);
                return name();
            }
            case "refresh_balance" -> {
                return name();
            }
        }

        return name();
    }

    /** Выполнить конвертацию монеты в USDT */
    private String executeSell(Long chatId) {
        String result;
        try {
            var settings = settingsService.getOrCreate(chatId);
            var keys = settingsService.getApiKey(chatId);
            ExchangeClient client = clientFactory.getClient(settings.getExchange());

            AccountInfo acc = client.fetchAccountInfo(keys.getPublicKey(), keys.getSecretKey(), settings.getNetwork());
            Optional<BalanceInfo> balanceOpt = acc.getBalances().stream()
                    .filter(b -> currentAsset.equalsIgnoreCase(b.getAsset()))
                    .findFirst();

            if (balanceOpt.isPresent()) {
                BalanceInfo b = balanceOpt.get();
                BigDecimal free = b.getFree();
                if (free.compareTo(BigDecimal.ZERO) > 0) {
                    String symbol = currentAsset + "USDT";
                    orderService.placeMarket(chatId, symbol, Order.Side.SELL, free.doubleValue());
                    result = "✅ Успешно конвертировано " + free.stripTrailingZeros().toPlainString()
                            + " " + currentAsset + " → USDT";
                } else {
                    result = "⚠️ Недостаточный баланс для конвертации.";
                }
            } else {
                result = "❌ Баланс по " + currentAsset + " не найден.";
            }
        } catch (Exception e) {
            log.error("Ошибка при конвертации {} → USDT: {}", currentAsset, e.getMessage(), e);
            result = "❌ Ошибка при конвертации: " + e.getMessage();
        }
        // ✅ теперь возвращаем просто name(), а результат передаём как notice
        return buildMessage(chatId, currentAsset, result).getText();
    }

    /** Выполнить покупку монеты на весь баланс USDT */
    private String executeBuy(Long chatId) {
        String result;
        try {
            var settings = settingsService.getOrCreate(chatId);
            var keys = settingsService.getApiKey(chatId);
            ExchangeClient client = clientFactory.getClient(settings.getExchange());

            AccountInfo acc = client.fetchAccountInfo(keys.getPublicKey(), keys.getSecretKey(), settings.getNetwork());
            Optional<BalanceInfo> usdtOpt = acc.getBalances().stream()
                    .filter(b -> "USDT".equalsIgnoreCase(b.getAsset()))
                    .findFirst();

            if (usdtOpt.isPresent()) {
                BalanceInfo usdt = usdtOpt.get();
                BigDecimal freeUsdt = usdt.getFree();
                if (freeUsdt.compareTo(BigDecimal.ZERO) > 0) {
                    String symbol = currentAsset + "USDT";
                    orderService.placeMarket(chatId, symbol, Order.Side.BUY, freeUsdt.doubleValue());
                    result = "✅ Куплено " + currentAsset + " на "
                            + freeUsdt.stripTrailingZeros().toPlainString() + " USDT";
                } else {
                    result = "⚠️ Недостаточный баланс USDT для покупки.";
                }
            } else {
                result = "❌ Баланс USDT не найден.";
            }
        } catch (Exception e) {
            log.error("Ошибка при покупке {} за USDT: {}", currentAsset, e.getMessage(), e);
            result = "❌ Ошибка при покупке: " + e.getMessage();
        }
        return buildMessage(chatId, currentAsset, result).getText();
    }

    private SendMessage buildMessage(Long chatId, String asset, String notice) {
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

        if (notice != null) {
            text += "\n\n" + notice;
        }

        // --- кнопки ---
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        if ("SELL".equals(pendingAction.get(chatId))) {
            rows.add(List.of(
                    InlineKeyboardButton.builder().text("✅ Да, продать").callbackData("confirm_yes").build(),
                    InlineKeyboardButton.builder().text("❌ Отмена").callbackData("confirm_no").build()
            ));
            text += "\n\n⚠️ Подтвердите продажу *" + asset + "* → USDT";
        } else if ("BUY".equals(pendingAction.get(chatId))) {
            rows.add(List.of(
                    InlineKeyboardButton.builder().text("✅ Да, купить").callbackData("confirm_yes").build(),
                    InlineKeyboardButton.builder().text("❌ Отмена").callbackData("confirm_no").build()
            ));
            text += "\n\n⚠️ Подтвердите покупку *" + asset + "* за USDT";
        } else {
            if (!"USDT".equalsIgnoreCase(asset)) {
                rows.add(List.of(
                        InlineKeyboardButton.builder().text("💱 Конвертировать в USDT").callbackData("convert_to_usdt").build(),
                        InlineKeyboardButton.builder().text("🛒 Купить на USDT").callbackData("buy_with_usdt").build()
                ));
            }
            rows.add(List.of(
                    InlineKeyboardButton.builder().text("🔄 Обновить").callbackData("refresh_balance").build(),
                    InlineKeyboardButton.builder().text("⬅️ Назад").callbackData("balance_assets").build()
            ));
        }

        return SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode("Markdown")
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                .build();
    }
}
