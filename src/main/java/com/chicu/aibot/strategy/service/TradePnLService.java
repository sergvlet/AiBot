package com.chicu.aibot.strategy.service;

import java.util.List;

public interface TradePnLService {
    PnLReport getRecent(Long chatId, String symbol, int lastDealsLimit);
}
