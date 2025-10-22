package com.chicu.aibot.bot.util;

import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

public final class TelegramText {
    private TelegramText() {}

    /** true, если это именно ошибка парсинга сущностей Telegram Markdown. */
    public static boolean isParseError(Throwable e) {
        if (e instanceof TelegramApiRequestException req) {
            String r = req.getApiResponse();
            String m = req.getMessage();
            return (r != null && r.contains("can't parse entities"))
                   || (m != null && m.contains("can't parse entities"));
        }
        return false;
    }

    /** Жёстко убираем markdown-метки — для фолбэка, когда форматирование мешает. */
    public static String stripMarkdown(String s) {
        if (s == null) return "";
        return s
                .replace("*", "")
                .replace("_", "")
                .replace("`", "")
                .replace("~", "");
    }

    /** Экранируем базовые символы Markdown (для Markdown V1). */
    public static String escapeMarkdownV1(String s) {
        if (s == null) return "";
        return s
                .replace("\\", "\\\\")
                .replace("_", "\\_")
                .replace("*", "\\*")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("`", "\\`");
    }

    /** ✅ Экранируем все специальные символы для MarkdownV2 (без ломки существующего кода). */
    public static String escapeMarkdownV2(String s) {
        if (s == null) return "";
        return s
                .replace("\\", "\\\\")
                .replace("_", "\\_")
                .replace("*", "\\*")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("~", "\\~")
                .replace("`", "\\`")
                .replace(">", "\\>")
                .replace("#", "\\#")
                .replace("+", "\\+")
                .replace("-", "\\-")
                .replace("=", "\\=")
                .replace("|", "\\|")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace(".", "\\.")
                .replace("!", "\\!");
    }
}
