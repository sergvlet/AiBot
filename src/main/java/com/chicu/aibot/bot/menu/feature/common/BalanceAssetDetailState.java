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
import java.util.concurrent.ConcurrentHashMap;

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
    private final Map<Long, String> pendingAction = new ConcurrentHashMap<>();

    /** Флеш-уведомление, показывается один раз при следующем render и очищается */
    private final Map<Long, String> flashNotice = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "balance_asset_detail";
    }

    @Override
    public SendMessage render(Long chatId) {
        // достаём и очищаем флеш-уведомление
        String notice = flashNotice.remove(chatId);
        return buildMessage(chatId, currentAsset, notice);
    }

    @Override
    public String handleInput(Update update) {
        if (!update.hasCallbackQuery()) return name();

        String data = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();

        switch (data) {
            case "balance_assets" -> {
                this.currentAsset = null;
                return "balance_assets";
            }
            case "convert_to_usdt" -> {
                pendingAction.put(chatId, "SELL");
                return name(); // остаёмся тут — покажем кнопки подтверждения
            }
            case "buy_with_usdt" -> {
                pendingAction.put(chatId, "BUY");
                return name();
            }
            case "confirm_yes" -> {
                String action = pendingAction.remove(chatId);
                if ("SELL".equals(action)) {
                    executeSell(chatId);
                    return name(); // после операции остаёмся здесь и покажем обновлённое меню
                }
                if ("BUY".equals(action)) {
                    executeBuy(chatId);
                    return name();
                }
                return name();
            }
            case "confirm_no" -> {
                pendingAction.remove(chatId);
                return name();
            }
            case "refresh_balance" -> {
                return name();
            }
            default -> {
                // закрытие ордера
                if (data.startsWith("cancel_order:")) {
                    String orderId = data.substring("cancel_order:".length());
                    try {
                        List<Order> active = orderService.loadActiveOrders(chatId, currentAsset + "USDT");
                        active.stream()
                                .filter(o -> orderId.equals(o.getId()))
                                .findFirst()
                                .ifPresent(o -> orderService.cancel(chatId, o));

                        flashNotice.put(chatId, "✅ Ордер " + orderId + " закрыт");
                    } catch (Exception e) {
                        flashNotice.put(chatId, "❌ Ошибка при закрытии ордера: " + e.getMessage());
                    }
                    return name(); // ВАЖНО: остаёмся в этом же меню
                }
                return name();
            }
        }
    }

    /** Конвертация монеты в USDT — кладём результат во флеш и остаёмся в этом меню */
    private void executeSell(Long chatId) {
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
                    result = "✅ Конвертировано " + free.stripTrailingZeros() + " " + currentAsset + " → USDT";
                } else {
                    result = "⚠️ Недостаточный баланс для конвертации.";
                }
            } else {
                result = "❌ Баланс по " + currentAsset + " не найден.";
            }
        } catch (Exception e) {
            result = "❌ Ошибка при конвертации: " + e.getMessage();
        }
        flashNotice.put(chatId, result);
    }

    /** Покупка монеты за USDT — кладём результат во флеш и остаёмся в этом меню */
    private void executeBuy(Long chatId) {
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
                    result = "✅ Куплено " + currentAsset + " на " + freeUsdt.stripTrailingZeros() + " USDT";
                } else {
                    result = "⚠️ Недостаточный баланс USDT для покупки.";
                }
            } else {
                result = "❌ Баланс USDT не найден.";
            }
        } catch (Exception e) {
            result = "❌ Ошибка при покупке: " + e.getMessage();
        }
        flashNotice.put(chatId, result);
    }

    /** Рендер баланса + кнопок */
    private SendMessage buildMessage(Long chatId, String asset, String notice) {
        if (asset == null) {
            return SendMessage.builder().chatId(chatId.toString()).text("❌ Монета не выбрана").build();
        }

        var settings = settingsService.getOrCreate(chatId);
        var keys = settingsService.getApiKey(chatId);
        ExchangeClient client = clientFactory.getClient(settings.getExchange());

        StringBuilder text = new StringBuilder();
        text.append("💰 *Баланс монеты ").append(asset).append("*\n\n");

        BigDecimal free = BigDecimal.ZERO;
        BigDecimal locked = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal usdValue = BigDecimal.ZERO;
        List<Order> active = Collections.emptyList();

        try {
            AccountInfo acc = client.fetchAccountInfo(keys.getPublicKey(), keys.getSecretKey(), settings.getNetwork());
            Optional<BalanceInfo> balanceOpt = acc.getBalances().stream()
                    .filter(b -> asset.equalsIgnoreCase(b.getAsset()))
                    .findFirst();

            if (balanceOpt.isPresent()) {
                BalanceInfo b = balanceOpt.get();
                free = b.getFree().setScale(8, RoundingMode.HALF_UP).stripTrailingZeros();
                locked = b.getLocked().setScale(8, RoundingMode.HALF_UP).stripTrailingZeros();
                total = free.add(locked);

                if (!"USDT".equalsIgnoreCase(asset)) {
                    Optional<TickerInfo> ticker = client.getTicker(asset + "USDT", settings.getNetwork());
                    if (ticker.isPresent()) {
                        usdValue = total.multiply(ticker.get().getPrice()).setScale(2, RoundingMode.HALF_UP);
                    }
                } else {
                    usdValue = total.setScale(2, RoundingMode.HALF_UP);
                }
            }

            // ордера
            active = orderService.loadActiveOrders(chatId, asset + "USDT");

        } catch (Exception e) {
            log.error("Ошибка загрузки данных: {}", e.getMessage());
        }

        text.append("Свободно: `").append(free).append("`\n");
        text.append("Заблокировано: `").append(locked).append("`\n");

        if (!active.isEmpty()) {
            text.append("📌 *Активные ордера:*\n");
            for (Order o : active) {
                text.append("• ").append(o.getSide())
                        .append(" ").append(o.getVolume())
                        .append(" @ ").append(o.getPrice())
                        .append(o.isFilled() ? " ✅" : " ⏳")
                        .append(" (id=").append(o.getId()).append(")\n");
            }
        } else {
            if (locked.compareTo(BigDecimal.ZERO) > 0) {
                text.append("📌 Ордеров нет (биржа показывает locked=").append(locked).append(")\n");
            } else {
                text.append("📌 Активных ордеров нет\n");
            }
        }

        text.append("Всего: `").append(total).append("`\n");
        text.append("💵 ~ В USDT: *").append(usdValue).append("*\n");

        if (notice != null && !notice.isBlank()) {
            text.append("\n").append(notice);
        }

        // --- кнопки ---
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        if ("SELL".equals(pendingAction.get(chatId))) {
            rows.add(List.of(
                    InlineKeyboardButton.builder().text("✅ Да, продать").callbackData("confirm_yes").build(),
                    InlineKeyboardButton.builder().text("❌ Отмена").callbackData("confirm_no").build()
            ));
        } else if ("BUY".equals(pendingAction.get(chatId))) {
            rows.add(List.of(
                    InlineKeyboardButton.builder().text("✅ Да, купить").callbackData("confirm_yes").build(),
                    InlineKeyboardButton.builder().text("❌ Отмена").callbackData("confirm_no").build()
            ));
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

            for (Order o : active) {
                rows.add(List.of(
                        InlineKeyboardButton.builder()
                                .text("❌ Закрыть ордер " + o.getId())
                                .callbackData("cancel_order:" + o.getId())
                                .build()
                ));
            }
        }

        return SendMessage.builder()
                .chatId(chatId.toString())
                .text(text.toString())
                .parseMode("Markdown")
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                .build();
    }
}
