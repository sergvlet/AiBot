package com.chicu.aibot.bot.menu.feature.common;

import com.chicu.aibot.bot.menu.core.MenuState;
import com.chicu.aibot.exchange.client.ExchangeClient;
import com.chicu.aibot.exchange.client.ExchangeClientFactory;
import com.chicu.aibot.exchange.model.AccountInfo;
import com.chicu.aibot.exchange.model.BalanceInfo;
import com.chicu.aibot.exchange.service.ExchangeSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component("balance_assets")
@RequiredArgsConstructor
public class BalanceAssetsState implements MenuState {

    private final ExchangeSettingsService settingsService;
    private final ExchangeClientFactory clientFactory;
    private final ApplicationContext applicationContext;

    private static final int PAGE_SIZE = 20;
    private static final int BUTTONS_PER_ROW = 5;

    // 🗂 Храним текущую страницу для каждого чата
    private final Map<Long, Integer> pages = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "balance_assets";
    }

    @Override
    public SendMessage render(Long chatId) {
        int page = pages.getOrDefault(chatId, 0);
        return buildMessage(chatId, page);
    }

    private SendMessage buildMessage(Long chatId, int page) {
        var settings = settingsService.getOrCreate(chatId);
        var keys = settingsService.getApiKey(chatId);
        ExchangeClient client = clientFactory.getClient(settings.getExchange());

        List<BalanceInfo> balances = new ArrayList<>();
        try {
            AccountInfo acc = client.fetchAccountInfo(
                    keys.getPublicKey(), keys.getSecretKey(), settings.getNetwork());
            balances = acc.getBalances();
        } catch (Exception e) {
            log.error("Ошибка загрузки балансов: {}", e.getMessage(), e);
        }

        int totalPages = (balances.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        if (totalPages == 0) totalPages = 1;
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        // сохраняем актуальную страницу
        pages.put(chatId, page);

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, balances.size());

        StringBuilder text = new StringBuilder("💰 *Баланс по монетам* (стр. " + (page + 1) + "/" + totalPages + ")\n\n");
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> currentRow = new ArrayList<>();

        for (int i = start; i < end; i++) {
            BalanceInfo b = balances.get(i);
            text.append(String.format("`%-6s` свободно: %s | заблокировано: %s\n",
                    b.getAsset(),
                    b.getFree().toPlainString(),
                    b.getLocked().toPlainString()));

            currentRow.add(InlineKeyboardButton.builder()
                    .text(b.getAsset())
                    .callbackData("balance_asset_detail:" + b.getAsset())
                    .build());

            if (currentRow.size() == BUTTONS_PER_ROW) {
                rows.add(currentRow);
                currentRow = new ArrayList<>();
            }
        }
        if (!currentRow.isEmpty()) rows.add(currentRow);

        // пагинация
        List<InlineKeyboardButton> nav = new ArrayList<>();
        if (page > 0) {
            nav.add(InlineKeyboardButton.builder()
                    .text("⏪ Назад")
                    .callbackData("balance_assets_page:" + (page - 1))
                    .build());
        }
        if (page < totalPages - 1) {
            nav.add(InlineKeyboardButton.builder()
                    .text("⏩ Вперёд")
                    .callbackData("balance_assets_page:" + (page + 1))
                    .build());
        }
        if (!nav.isEmpty()) rows.add(nav);

        // кнопка назад
        rows.add(List.of(InlineKeyboardButton.builder()
                .text("⬅️ В меню баланса")
                .callbackData("balance_menu")
                .build()));

        return SendMessage.builder()
                .chatId(chatId.toString())
                .text(text.toString())
                .parseMode("Markdown")
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build())
                .build();
    }

    @Override
    public String handleInput(Update update) {
        if (!update.hasCallbackQuery()) return name();

        String data = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();

        if (data.startsWith("balance_asset_detail:")) {
            String asset = data.substring("balance_asset_detail:".length());
            log.info("Выбрана монета {} для chatId={}", asset, chatId);

            BalanceAssetDetailState detailState =
                    applicationContext.getBean(BalanceAssetDetailState.class);
            detailState.setCurrentAsset(asset);

            return "balance_asset_detail";
        }

        if (data.startsWith("balance_assets_page:")) {
            int newPage = Integer.parseInt(data.substring("balance_assets_page:".length()));
            pages.put(chatId, newPage); // ✅ сохраняем новую страницу
            return name();
        }

        return "balance_menu";
    }
}
