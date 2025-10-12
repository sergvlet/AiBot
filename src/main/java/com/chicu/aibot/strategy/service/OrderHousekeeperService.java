package com.chicu.aibot.strategy.service;

public interface OrderHousekeeperService {
    /** Приводит открытые ордера к консистентному виду и возвращает фактическое число активных per side. */
    HousekeepingResult reconcile(Long chatId, String symbol, int maxActivePerSide);
}
