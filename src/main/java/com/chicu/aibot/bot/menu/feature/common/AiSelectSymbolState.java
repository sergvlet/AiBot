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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
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
    public String name() { return NAME; }

    @Override
    public SendMessage render(Long chatId) {
        // Привяжем сервис стратегии по returnState, если ещё не привязали
        if (sessionService.getAttribute(chatId, KEY_SVC) == null) {
            String from = sessionService.getReturnState(chatId);
            symbolServices.entrySet().stream()
                    .filter(e -> Objects.equals(e.getValue().getReturnState(), from))
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
        if (back == null || back.isBlank()) back = "ai_trading";

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
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                .build();
    }

    private SendMessage renderPage(Long chatId, List<String> all, int page, String category) {
        ExchangeSettings ex     = settingsService.getOrCreate(chatId);
        ExchangeClient   client = clientFactory.getClient(ex.getExchange());
        NetworkType      net    = ex.getNetwork();

        int total   = all.size();
        int maxPage = Math.max(1, (int) Math.ceil(total / (double) PAGE_SIZE));
        int curPage = Math.min(Math.max(1, page), maxPage);

        int from = (curPage - 1) * PAGE_SIZE;
        int to   = Math.min(from + PAGE_SIZE, total);
        List<String> slice = all.subList(from, to);

        String catLabel = switch (category) {
            case "symbol_popular" -> "Популярные";
            case "symbol_gainers" -> "Лидеры роста";
            case "symbol_losers"  -> "Лидеры падения";
            case "symbol_volume"  -> "По объёму";
            default               -> "—";
        };

        StringBuilder text = new StringBuilder();
        text.append(String.format("*Категория: %s*\nПары %d–%d из %d\n\n",
                catLabel, total == 0 ? 0 : from + 1, to, total));

        List<String> lines = new ArrayList<>();
        for (String sym : slice) {
            try {
                TickerInfo info = client.getTicker(sym, net);
                if (info == null || info.getPrice() == null || info.getChangePct() == null) continue;

                BigDecimal price = info.getPrice().setScale(2, RoundingMode.HALF_UP);
                BigDecimal pct   = info.getChangePct().setScale(2, RoundingMode.HALF_UP);
                String arrow = pct.signum() >= 0 ? "↑" : "↓";

                lines.add(String.format("%s: %s %s%% %s", sym, price.toPlainString(), pct.toPlainString(), arrow));
            } catch (RuntimeException ignore) {
                // пропускаем проблемные символы
            }
        }

        for (int i = 0; i < lines.size(); i += 2) {
            if (i + 1 < lines.size()) {
                text.append(lines.get(i)).append("    ").append(lines.get(i + 1)).append("\n");
            } else {
                text.append(lines.get(i)).append("\n");
            }
        }
        if (lines.isEmpty()) text.append("_Нет данных для показа по этой категории._\n");

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (int i = 0; i < slice.size(); i += 4) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            for (String sym : slice.subList(i, Math.min(i + 4, slice.size()))) {
                row.add(button(sym, "symbol_select_" + sym));
            }
            if (!row.isEmpty()) rows.add(row);
        }

        List<InlineKeyboardButton> nav = new ArrayList<>();
        if (curPage > 1)        nav.add(button("‹ Назад", "symbol_page_" + (curPage - 1)));
        if (to < total)         nav.add(button("› Далее", "symbol_page_" + (curPage + 1)));
        if (!nav.isEmpty())     rows.add(nav);

        rows.add(List.of(button("‹ Категории", NAME)));

        return SendMessage.builder()
                .chatId(chatId.toString())
                .text(text.toString())
                .parseMode("Markdown")
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                .build();
    }

    @Override
    public String handleInput(Update update) {
        String data   = update.getCallbackQuery().getData();
        Long   chatId = update.getCallbackQuery().getMessage().getChatId();

        // 0) Если это «возврат» в состояние, откуда пришли (например, ai_trading_scalping_config) — отдадим управление туда
        String backState = sessionService.getReturnState(chatId);
        if (backState != null && backState.equals(data)) {
            clearSessionAttrs(chatId, /*keepSvc*/ false);
            return backState;
        }

        String svcKey = sessionService.getAttribute(chatId, KEY_SVC);
        SymbolSettingsService symbolSvc = symbolServices.get(svcKey);
        if (symbolSvc == null) {
            String fallback = backState;
            if (fallback == null || fallback.isBlank()) fallback = "ai_trading";
            log.error("SymbolSettingsService для '{}' не найден, возвращаем '{}'", svcKey, fallback);
            clearSessionAttrs(chatId, /*keepSvc*/ false);
            return fallback;
        }

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
            try {
                int page = Integer.parseInt(data.substring("symbol_page_".length()));
                sessionService.setAttribute(chatId, KEY_PAGE, Math.max(1, page));
            } catch (NumberFormatException ignore) {}
            return NAME;
        }

        // 3) Выбор конкретной пары
        if (data.startsWith("symbol_select_")) {
            String symbol = data.substring("symbol_select_".length());
            Object settings = symbolSvc.getOrCreate(chatId);
            symbolSvc.saveSymbol(chatId, settings, symbol);

            clearSessionAttrs(chatId, /*keepSvc*/ false);
            return symbolSvc.getReturnState();
        }

        // 4) Вернуться в меню категорий этого экрана
        if (NAME.equals(data)) {
            clearSessionAttrs(chatId, /*keepSvc*/ true); // стратегию помним
            return NAME;
        }

        // 5) Любой иной колбэк (если вдруг положили сюда кнопку с именем состояния) — передаём дальше фреймворка
        return !data.isBlank() ? data : NAME;
    }

    private void clearSessionAttrs(Long chatId, boolean keepSvc) {
        sessionService.removeAttribute(chatId, KEY_LIST);
        sessionService.removeAttribute(chatId, KEY_PAGE);
        sessionService.removeAttribute(chatId, KEY_CATEGORY);
        if (!keepSvc) sessionService.removeAttribute(chatId, KEY_SVC);
    }

    private InlineKeyboardButton button(String text, String data) {
        return InlineKeyboardButton.builder().text(text).callbackData(data).build();
    }
}
