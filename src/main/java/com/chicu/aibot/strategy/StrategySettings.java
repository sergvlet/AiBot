// src/main/java/com/chicu/aibot/bot/strategy/StrategySettings.java
package com.chicu.aibot.strategy;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public abstract class StrategySettings {

    @Id
    private Long chatId;

    /** Тип текущей стратегии */
    public abstract StrategyType getType();

    /** Таймфрейм, напр. "1m", "1h" */
    public abstract String getTimeframe();

    /** Сколько свечей брать при анализе */
    public abstract Integer getCachedCandlesLimit();
}
