package com.chicu.aibot.bot.ui;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Адаптивная и "красиво балансируемая" раскладка Inline-кнопок для Telegram.
 * Фичи:
 *  • выбор колонок по длине подписей (1.. N),
 *  • верхний предел колонок (например, 2),
 *  • балансировка последней строки (без "висячей" одиночной кнопки).
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AdaptiveKeyboard {

    // Пороги длины подписи (под мобильный Telegram).
    private static final int L1 = 24; // >24  → 1 в ряд
    private static final int L2 = 16; // >16  → 2 в ряд
    private static final int L3 = 12; // >12  → 3 в ряд
    private static final int L4 = 8;  // >8   → 4 в ряд
    private static final int MAX_COLS = 5;

    /** Быстрый хелпер создания кнопки. */
    public static InlineKeyboardButton btn(String text, String data) {
        return InlineKeyboardButton.builder().text(text).callbackData(data).build();
    }

    /** Удобный хелпер для сбора одной "группы" (строки/набора строк) из произвольного количества кнопок. */
    public static List<InlineKeyboardButton> row(InlineKeyboardButton... buttons) {
        List<InlineKeyboardButton> list = new ArrayList<>();
        if (buttons != null) {
            for (InlineKeyboardButton b : buttons) if (b != null) list.add(b);
        }
        return list;
    }

    /* ========================== ПУБЛИЧНЫЕ API ========================== */

    /** Адаптивная раскладка без ограничений (до MAX_COLS). */
    public static InlineKeyboardMarkup markupFromGroups(Collection<List<InlineKeyboardButton>> groups) {
        return InlineKeyboardMarkup.builder()
                .keyboard(toAdaptiveRowsGroups(groups, MAX_COLS))
                .build();
    }

    /**
     * Адаптивная раскладка с верхним пределом колонок (например, 2).
     * Для большинства кейсов "красиво и читабельно": maxColsPerRow = 2.
     */
    public static InlineKeyboardMarkup markupFromGroups(Collection<List<InlineKeyboardButton>> groups, int maxColsPerRow) {
        return InlineKeyboardMarkup.builder()
                .keyboard(toAdaptiveRowsGroups(groups, maxColsPerRow))
                .build();
    }

    /* ======================== ВНУТРЕННЯЯ ЛОГИКА ======================== */

    /** Разложить набор групп с ограничением на максимум колонок. */
    private static List<List<InlineKeyboardButton>> toAdaptiveRowsGroups(Collection<List<InlineKeyboardButton>> groups,
                                                                         int maxColsPerRow) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        if (groups == null) return rows;
        for (List<InlineKeyboardButton> g : groups) {
            rows.addAll(layoutGroupBalanced(g, maxColsPerRow));
        }
        return rows;
    }

    /**
     * Разложение одной группы:
     * 1) считаем максимальную длину подписи;
     * 2) определяем "авто" количество колонок по порогам;
     * 3) ограничиваем сверху maxColsPerRow;
     * 4) равномерно балансируем строки (чтобы не было одиночной последней кнопки).
     */
    private static List<List<InlineKeyboardButton>> layoutGroupBalanced(List<InlineKeyboardButton> items,
                                                                        int maxColsPerRow) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        if (items == null || items.isEmpty()) return rows;

        int longest = 0;
        for (InlineKeyboardButton b : items) {
            longest = Math.max(longest, visibleLen(b.getText()));
        }

        int autoCols = capacityForLen(longest);          // 1..MAX_COLS по длинам
        int colsCap  = safeCols(maxColsPerRow);          // 1..MAX_COLS по внешнему ограничению
        int cols     = Math.max(1, Math.min(autoCols, colsCap));

        // Равномерная упаковка: считаем число рядов и распределяем кнопки без "хвоста" в 1 штуку
        rows.addAll(packBalanced(items, cols));
        return rows;
    }

    /**
     * Балансирующая упаковка: стараемся разложить по r = ceil(n/cols) строкам,
     * распределяя остаток по верхним строкам (получаются размеры вроде 3-3-2, а не 3-3-1-1).
     */
    private static List<List<InlineKeyboardButton>> packBalanced(List<InlineKeyboardButton> items, int cols) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        int n = items.size();
        cols = Math.max(1, cols);

        // базовое количество строк по "жёстким" колонкам
        int rowsCount = (n + cols - 1) / cols; // ceil(n/cols)
        rowsCount = Math.max(1, rowsCount);

        // базовый размер строки и "излишек", который раскинем по верхним строкам
        int base = n / rowsCount;      // минимальный размер строки
        int extra = n % rowsCount;     // столько верхних строк получат +1

        // гарантируем, что ни одна строка не длиннее cols
        base = Math.min(base, cols);
        int maxRowSize = Math.min(base + 1, cols);

        int idx = 0;
        for (int r = 0; r < rowsCount; r++) {
            int need = (r < extra) ? (base + 1) : base;
            need = Math.min(need, maxRowSize);
            List<InlineKeyboardButton> row = new ArrayList<>(need);
            for (int i = 0; i < need && idx < n; i++, idx++) {
                row.add(items.get(idx));
            }
            if (!row.isEmpty()) rows.add(row);
        }

        // Если что-то вдруг осталось (из-за страховочных min/max) — докинем в последнюю строку/создадим новую
        while (idx < n) {
            List<InlineKeyboardButton> last = rows.isEmpty() ? null : rows.getLast();
            if (last != null && last.size() < maxRowSize) {
                last.add(items.get(idx++));
            } else {
                List<InlineKeyboardButton> row = new ArrayList<>();
                row.add(items.get(idx++));
                rows.add(row);
            }
        }
        return rows;
    }

    /* ============================ УТИЛИТЫ ============================ */

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

    private static int safeCols(int v) {
        if (v <= 0) return MAX_COLS;
        return Math.min(v, MAX_COLS);
    }
}
