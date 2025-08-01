// src/main/java/com/chicu/aibot/bot/menu/feature/common/AiSelectSymbolState.java
package com.chicu.aibot.bot.menu.feature.common;

import com.chicu.aibot.bot.menu.core.MenuSessionService;
import com.chicu.aibot.bot.menu.core.MenuState;
import com.chicu.aibot.exchange.client.ExchangeClient;
import com.chicu.aibot.exchange.client.ExchangeClientFactory;
import com.chicu.aibot.exchange.enums.NetworkType;
import com.chicu.aibot.exchange.model.ExchangeSettings;
import com.chicu.aibot.exchange.model.TickerInfo;
import com.chicu.aibot.exchange.service.ExchangeSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.math.RoundingMode;
import java.util.*;

@Component
@RequiredArgsConstructor
public class AiSelectSymbolState implements MenuState {
    public static final String NAME = "ai_select_symbol";

    private final MenuSessionService sessionService;
    private final ExchangeSettingsService settingsService;
    private final ExchangeClientFactory clientFactory;
    private final Map<String, SymbolSettingsService> symbolServices;

    private static final int PAGE_SIZE      = 30;
    private static final String KEY_LIST     = "symbol_list";
    private static final String KEY_PAGE     = "symbol_page";
    private static final String KEY_CATEGORY = "symbol_category";
    private static final String KEY_SVC      = "symbol_strategy";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public SendMessage render(Long chatId) {
        // При первом заходе запомним, для какой стратегии выбираем символ
        if (sessionService.getAttribute(chatId, KEY_SVC) == null) {
            String from = sessionService.getReturnState(chatId);
            symbolServices.entrySet().stream()
                    .filter(e -> e.getValue().getReturnState().equals(from))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .ifPresent(key -> sessionService.setAttribute(chatId, KEY_SVC, key));
        }

        List<String> all      = sessionService.getAttribute(chatId, KEY_LIST);
        Integer      page     = sessionService.getAttribute(chatId, KEY_PAGE);
        String       category = sessionService.getAttribute(chatId, KEY_CATEGORY);

        if (all != null && page != null && category != null) {
            return renderPage(chatId, all, page, category);
        }
        return renderMenu(chatId);
    }

    private SendMessage renderMenu(Long chatId) {
        String back = sessionService.getReturnState(chatId);
        if (back == null) back = "ai_trading";

        String text = "*Выбор торговой пары*\n\nВыберите категорию или «Назад»:";
        List<List<InlineKeyboardButton>> rows = List.of(
                List.of(
                        button("🔥 Популярные",    "symbol_popular"),
                        button("📈 Лидеры роста",  "symbol_gainers")
                ),
                List.of(
                        button("📉 Лидеры падения","symbol_losers"),
                        button("💰 По объёму",     "symbol_volume")
                ),
                List.of(
                        button("‹ Назад", back)
                )
        );

        return SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode("Markdown")
                .replyMarkup(new InlineKeyboardMarkup(rows))
                .build();
    }

    private SendMessage renderPage(Long chatId,
                                   List<String> all,
                                   int page,
                                   String category) {
        ExchangeSettings ex     = settingsService.getOrCreate(chatId);
        ExchangeClient   client = clientFactory.getClient(ex.getExchange());
        NetworkType      net    = ex.getNetwork();

        int total = all.size();
        int from  = (page - 1) * PAGE_SIZE;
        int to    = Math.min(from + PAGE_SIZE, total);
        List<String> slice = all.subList(from, to);

        // 1) Составляем заголовок и тело сообщения с ценой и % изменения
        String catLabel = switch (category) {
            case "symbol_popular" -> "Популярные";
            case "symbol_gainers" -> "Лидеры роста";
            case "symbol_losers"  -> "Лидеры падения";
            case "symbol_volume"  -> "По объёму";
            default               -> "";
        };
        StringBuilder text = new StringBuilder();
        text.append(String.format("*Категория: %s*\nПары %d–%d из %d\n\n",
                catLabel, from + 1, to, total));

        for (String sym : slice) {
            TickerInfo info = client.getTicker(sym, net);
            if (info == null) continue;
            String price  = info.getPrice()
                    .setScale(2, RoundingMode.HALF_UP)
                    .toPlainString();
            String pct    = info.getChangePct()
                    .setScale(2, RoundingMode.HALF_UP)
                    .toPlainString();
            String arrow  = info.getChangePct().signum() >= 0 ? "↑" : "↓";
            text.append(String.format("%s: %s %s%s%%\n", sym, price, arrow, pct));
        }

        // 2) Кнопки — по три символа в ряд
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (int i = 0; i < slice.size(); i += 4) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            for (String sym : slice.subList(i, Math.min(i + 4, slice.size()))) {
                row.add(button(sym, "symbol_select_" + sym));
            }
            rows.add(row);
        }

        // 3) Навигационная строка
        List<InlineKeyboardButton> nav = new ArrayList<>();
        if (page > 1)   nav.add(button("‹ Назад", "symbol_page_" + (page - 1)));
        if (to < total) nav.add(button("› Далее", "symbol_page_" + (page + 1)));
        rows.add(nav);

        // 4) Кнопка возврата в категории
        rows.add(List.of(button("‹ Категории", NAME)));

        return SendMessage.builder()
                .chatId(chatId.toString())
                .text(text.toString())
                .parseMode("Markdown")
                .replyMarkup(new InlineKeyboardMarkup(rows))
                .build();
    }

    @Override
    public String handleInput(Update update) {
        String data   = update.getCallbackQuery().getData();
        Long   chatId = update.getCallbackQuery().getMessage().getChatId();

        String svcKey = sessionService.getAttribute(chatId, KEY_SVC);
        SymbolSettingsService symbolSvc = symbolServices.get(svcKey);

        // 1) Выбрали категорию
        if (data.startsWith("symbol_")
                && !data.startsWith("symbol_page_")
                && !data.startsWith("symbol_select_")) {

            sessionService.setAttribute(chatId, KEY_CATEGORY, data);
            ExchangeSettings ex     = settingsService.getOrCreate(chatId);
            ExchangeClient   client = clientFactory.getClient(ex.getExchange());
            List<String> all = switch (data) {
                case "symbol_popular" -> client.fetchPopularSymbols();
                case "symbol_gainers" -> client.fetchGainers();
                case "symbol_losers"  -> client.fetchLosers();
                case "symbol_volume"  -> client.fetchByVolume();
                default               -> Collections.emptyList();
            };
            sessionService.setAttribute(chatId, KEY_LIST, all);
            sessionService.setAttribute(chatId, KEY_PAGE, 1);
            return NAME;
        }

        // 2) Пагинация
        if (data.startsWith("symbol_page_")) {
            int page = Integer.parseInt(data.substring("symbol_page_".length()));
            sessionService.setAttribute(chatId, KEY_PAGE, page);
            return NAME;
        }

        // 3) Выбор конкретной пары
        if (data.startsWith("symbol_select_")) {
            String symbol = data.substring("symbol_select_".length());
            Object settings = symbolSvc.getOrCreate(chatId);
            symbolSvc.saveSymbol(chatId, settings, symbol);
            // очищаем
            sessionService.removeAttribute(chatId, KEY_LIST);
            sessionService.removeAttribute(chatId, KEY_PAGE);
            sessionService.removeAttribute(chatId, KEY_CATEGORY);
            sessionService.removeAttribute(chatId, KEY_SVC);
            return symbolSvc.getReturnState();
        }

        // 4) Вернуться в меню категорий
        if (NAME.equals(data)) {
            sessionService.removeAttribute(chatId, KEY_LIST);
            sessionService.removeAttribute(chatId, KEY_PAGE);
            sessionService.removeAttribute(chatId, KEY_CATEGORY);
            // KEY_SVC оставляем, чтобы при повторном заходе помнить стратегию
            return NAME;
        }

        return NAME;
    }

    private InlineKeyboardButton button(String text, String data) {
        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(data)
                .build();
    }
}
