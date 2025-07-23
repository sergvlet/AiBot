package com.chicu.aibot.strategy.model;

import com.chicu.aibot.strategy.StrategyType;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "ai_trading_settings")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class AiTradingSettings {

    @Id
    @Column(name = "chat_id")
    private Long chatId;

    /** Пользователь может выбрать сразу несколько стратегий */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "ai_trading_selected_strategies",
            joinColumns = @JoinColumn(name = "chat_id")
    )
    @Column(name = "strategy", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<StrategyType> selectedStrategies = new HashSet<>();
}
