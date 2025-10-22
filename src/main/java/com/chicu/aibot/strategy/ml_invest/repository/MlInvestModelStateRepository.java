package com.chicu.aibot.strategy.ml_invest.repository;

import com.chicu.aibot.strategy.ml_invest.model.MlInvestModelState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MlInvestModelStateRepository extends JpaRepository<MlInvestModelState, Long> {
    Optional<MlInvestModelState> findTopByChatIdOrderByLastTrainAtDesc(Long chatId);
}
