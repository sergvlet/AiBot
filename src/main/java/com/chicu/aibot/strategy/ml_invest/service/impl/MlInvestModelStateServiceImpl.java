package com.chicu.aibot.strategy.ml_invest.service.impl;

import com.chicu.aibot.strategy.ml_invest.model.MlInvestModelState;
import com.chicu.aibot.strategy.ml_invest.repository.MlInvestModelStateRepository;
import com.chicu.aibot.strategy.ml_invest.service.MlInvestModelStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MlInvestModelStateServiceImpl implements MlInvestModelStateService {

    private final MlInvestModelStateRepository repository;

    @Override
    public MlInvestModelState getOrCreate(Long chatId) {
        return repository.findTopByChatIdOrderByLastTrainAtDesc(chatId)
                .orElse(MlInvestModelState.builder()
                        .chatId(chatId)
                        .status("no_model")
                        .lastTrainAt(LocalDateTime.MIN)
                        .build());
    }

    @Override
    public MlInvestModelState saveState(MlInvestModelState state) {
        return repository.save(state);
    }
}
