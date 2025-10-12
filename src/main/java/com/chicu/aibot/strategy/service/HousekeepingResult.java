package com.chicu.aibot.strategy.service;

import lombok.Builder;
import lombok.Value;

@Value @Builder
public class HousekeepingResult {
    int buyActive;   // сколько BUY реально осталось
    int sellActive;  // сколько SELL реально осталось
    int removedDb;   // сколько удалили из БД как несуществующие
    int cancelled;   // сколько отменили как дубли/лишние
}
