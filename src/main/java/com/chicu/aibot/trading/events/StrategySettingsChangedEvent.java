package com.chicu.aibot.trading.events;

import com.chicu.aibot.strategy.StrategyType;

/** Событие изменения настроек стратегии для конкретного chatId. */
public record StrategySettingsChangedEvent(Long chatId, StrategyType strategyType) { }
