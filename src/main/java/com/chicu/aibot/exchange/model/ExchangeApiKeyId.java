package com.chicu.aibot.exchange.model;

import com.chicu.aibot.exchange.enums.Exchange;
import com.chicu.aibot.exchange.enums.NetworkType;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExchangeApiKeyId implements Serializable {
    @Column(name = "chat_id")
    private Long chatId;

    @Enumerated(EnumType.STRING)
    @Column(name = "exchange")
    private Exchange exchange;

    @Enumerated(EnumType.STRING)
    @Column(name = "network")
    private NetworkType network;
}
