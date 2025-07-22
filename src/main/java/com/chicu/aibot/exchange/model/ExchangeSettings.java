package com.chicu.aibot.exchange.model;

import com.chicu.aibot.exchange.enums.Exchange;
import com.chicu.aibot.exchange.enums.NetworkType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "exchange_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExchangeSettings {

    @Id
    @Column(name = "chat_id")
    private Long chatId;

    @Enumerated(EnumType.STRING)
    @Column(name = "exchange", nullable = false)
    private Exchange exchange;

    @Enumerated(EnumType.STRING)
    @Column(name = "network", nullable = false)
    private NetworkType network;
}
