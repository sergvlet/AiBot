package com.chicu.aibot.exchange.model;

import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountInfo {
    private String accountId;
    private List<BalanceInfo> balances;
}
