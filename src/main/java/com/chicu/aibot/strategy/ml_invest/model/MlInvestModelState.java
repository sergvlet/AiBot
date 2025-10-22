package com.chicu.aibot.strategy.ml_invest.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ml_invest_model_state")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MlInvestModelState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long chatId;

    /** Путь к модели (.joblib) */
    private String modelPath;

    /** Путь к последнему датасету */
    private String datasetPath;

    /** Таймфрейм, на котором обучалась */
    private String timeframe;

    /** Размер обучающего окна в днях */
    private Integer trainingWindowDays;

    /** Количество инструментов в обучении */
    private Integer universeSize;

    /** Дата последнего обучения */
    private LocalDateTime lastTrainAt;

    /** Accuracy последнего обучения */
    private BigDecimal accuracy;

    /** Версия модели (например 1.0.3) */
    private String version;

    /** Статус модели */
    private String status;
}
