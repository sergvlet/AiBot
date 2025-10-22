package com.chicu.aibot.python;

import java.time.Duration;
import java.util.Map;

public interface PythonInferenceService {

    void trainIfNeeded(String datasetPath,
                       Long chatId,
                       String modelDir,
                       String timeframe,
                       Duration retrainPeriod,
                       boolean force);

    Map<String, Double> predictProba(String modelDir,
                                     Long chatId,
                                     String timeframe,
                                     String datasetPath);
}
