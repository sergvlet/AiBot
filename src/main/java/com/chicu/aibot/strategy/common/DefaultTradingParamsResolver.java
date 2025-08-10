package com.chicu.aibot.strategy.common;

import com.chicu.aibot.exchange.model.ExchangeSettings;
import com.chicu.aibot.exchange.service.ExchangeSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultTradingParamsResolver {

    private final ExchangeSettingsService exchangeSettingsService;

    public String resolveSymbol(Long chatId, String fallback, String hardDefault) {
        try {
            ExchangeSettings es = exchangeSettingsService.getOrCreate(chatId);
            String v = tryStringGetter(es,
                "getSymbol", "getDefaultSymbol", "getBaseSymbol", "getSelectedSymbol");
            if (v != null && !v.isBlank()) return v;
        } catch (Exception e) {
            log.debug("Не удалось получить символ из ExchangeSettings: {}", e.getMessage());
        }
        return orDefault(fallback, hardDefault);
    }

    public String resolveTimeframe(Long chatId, String fallback, String hardDefault) {
        try {
            ExchangeSettings es = exchangeSettingsService.getOrCreate(chatId);
            String v = tryStringGetter(es,
                "getTimeframe", "getDefaultTimeframe", "getInterval", "getSelectedTimeframe");
            if (v != null && !v.isBlank()) return v;
        } catch (Exception e) {
            log.debug("Не удалось получить таймфрейм из ExchangeSettings: {}", e.getMessage());
        }
        return orDefault(fallback, hardDefault);
    }

    private String tryStringGetter(Object target, String... methodNames) {
        for (String name : methodNames) {
            try {
                Method m = target.getClass().getMethod(name);
                Object val = m.invoke(target);
                if (val instanceof String s && !s.isBlank()) return s;
            } catch (NoSuchMethodException ignored) {
            } catch (Exception ex) {
                log.debug("Ошибка вызова {}: {}", name, ex.getMessage());
            }
        }
        return null;
    }

    private String orDefault(String prop, String hard) {
        return (prop == null || prop.isBlank()) ? hard : prop;
    }
}
