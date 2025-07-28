package com.chicu.aibot.strategy;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.*;
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

    /** Активна ли стратегия */
    @Builder.Default
    @Column(name = "active", nullable = false)
    protected boolean active = false;

    /** Тип текущей стратегии */
    public abstract StrategyType getType();

    /** Таймфрейм, напр. "1m", "1h" */
    public abstract String getTimeframe();

    /** Сколько свечей брать при анализе */
    public abstract Integer getCachedCandlesLimit();

}
