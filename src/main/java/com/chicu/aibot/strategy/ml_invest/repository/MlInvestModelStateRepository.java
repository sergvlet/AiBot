package com.chicu.aibot.strategy.ml_invest.repository;

import com.chicu.aibot.strategy.ml_invest.model.MlInvestModelState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MlInvestModelStateRepository extends JpaRepository<MlInvestModelState, Long> {
    Optional<MlInvestModelState> findTopByChatIdOrderByLastTrainAtDesc(Long chatId);
}
