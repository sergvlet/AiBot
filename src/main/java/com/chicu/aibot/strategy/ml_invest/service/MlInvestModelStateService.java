package com.chicu.aibot.strategy.ml_invest.service;

import com.chicu.aibot.strategy.ml_invest.model.MlInvestModelState;

public interface MlInvestModelStateService {
    MlInvestModelState getOrCreate(Long chatId);
    MlInvestModelState saveState(MlInvestModelState state);
}
