package com.chicu.aibot.exchange.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "exchange_api_keys")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExchangeApiKey {

    @EmbeddedId
    private ExchangeApiKeyId id;

    @Column(name = "public_key", nullable = false)
    private String publicKey;

    @Column(name = "secret_key", nullable = false)
    private String secretKey;
}
