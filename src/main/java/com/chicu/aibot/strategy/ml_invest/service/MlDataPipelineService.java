package com.chicu.aibot.strategy.ml_invest.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public interface MlDataPipelineService {
    List<String> pickUniverse(Long chatId, String timeframe, int universeSize, BigDecimal min24hQuoteVolume);
    Dataset buildTrainingDataset(Long chatId, List<String> symbols, String timeframe, int windowDays);
    String trainIfNeeded(Long chatId, Dataset dataset, Duration retrainIfOlderThan, boolean force);
    Map<String, Probabilities> predict(Long chatId, String modelRef, List<String> symbols, String timeframe);

    class Dataset {
        public final List<String> symbols; public final String timeframe;
        public final int rows, cols; public final java.nio.file.Path file;
        public Dataset(List<String> s, String tf, int r, int c, java.nio.file.Path f){ symbols=s; timeframe=tf; rows=r; cols=c; file=f; }
    }
    class Probabilities { public final double buy,sell,hold; public Probabilities(double b,double s,double h){buy=b;sell=s;hold=h;} }
}
