package com.chicu.aibot.exchange.model;

import lombok.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Balance {
    private String asset;
    private BigDecimal free;
    private BigDecimal locked;
}
