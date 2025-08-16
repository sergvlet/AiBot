package com.chicu.aibot.bot.ui;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Универсальная адаптивная раскладка Inline-кнопок Telegram:
 * длинные подписи — 1–2 в ряд, средние — 3, короткие — 4–5.
 * Порядок кнопок сохраняется. Логику бота не меняет.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AdaptiveKeyboard {

    // Пороговые длины (под мобильный Telegram). При желании можно подрегулировать.
    private static final int L1 = 24; // >24 -> 1 в ряд
    private static final int L2 = 16; // >16 -> 2 в ряд
    private static final int L3 = 12; // >12 -> 3 в ряд
    private static final int L4 = 8;  // >8  -> 4 в ряд
    private static final int MAX_COLS = 5;

    /** Быстрый хелпер создания кнопки (по желанию). */
    public static InlineKeyboardButton btn(String text, String data) {
        return InlineKeyboardButton.builder().text(text).callbackData(data).build();
    }

    /** Адаптивная разбивка одной группы кнопок на строки. */
    public static List<List<InlineKeyboardButton>> toAdaptiveRows(List<InlineKeyboardButton> items) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        if (items == null || items.isEmpty()) return rows;

        List<InlineKeyboardButton> row = new ArrayList<>();
        int currentMaxLen = 0;
        int capacity;

        for (InlineKeyboardButton b : items) {
            int len = visibleLen(b.getText());
            int newMax = Math.max(currentMaxLen, len);
            int newCapacity = capacityForLen(newMax);

            if (!row.isEmpty() && row.size() >= newCapacity) {
                rows.add(row);
                row = new ArrayList<>();
                currentMaxLen = 0;
            }

            row.add(b);
            currentMaxLen = Math.max(currentMaxLen, len);
            capacity = capacityForLen(currentMaxLen);

            if (row.size() >= capacity) {
                rows.add(row);
                row = new ArrayList<>();
                currentMaxLen = 0;
            }
        }
        if (!row.isEmpty()) rows.add(row);
        return rows;
    }

    /** Адаптивная разбивка набора групп (каждую группу раскладывает отдельно, порядок групп сохраняется). */
    public static List<List<InlineKeyboardButton>> toAdaptiveRowsGroups(Collection<List<InlineKeyboardButton>> groups) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        if (groups == null) return rows;
        for (List<InlineKeyboardButton> g : groups) {
            rows.addAll(toAdaptiveRows(g));
        }
        return rows;
    }

    /** Сборка готовой разметки из групп. */
    public static InlineKeyboardMarkup markupFromGroups(Collection<List<InlineKeyboardButton>> groups) {
        return InlineKeyboardMarkup.builder().keyboard(toAdaptiveRowsGroups(groups)).build();
    }

    private static int capacityForLen(int maxLen) {
        if (maxLen > L1) return 1;
        if (maxLen > L2) return 2;
        if (maxLen > L3) return 3;
        if (maxLen > L4) return 4;
        return MAX_COLS;
    }

    private static int visibleLen(String s) {
        if (s == null) return 0;
        return s.codePointCount(0, s.length());
    }
}
