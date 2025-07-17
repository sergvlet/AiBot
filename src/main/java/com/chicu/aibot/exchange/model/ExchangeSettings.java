package com.chicu.aibot.exchange.model;

import com.chicu.aibot.exchange.Exchange;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "exchange_settings")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class ExchangeSettings {

    @Id
    @Column(name = "chat_id")
    private Long chatId;

    @Enumerated(EnumType.STRING)
    @Column(name = "exchange", nullable = false)
    private Exchange exchange;
}
