package com.chicu.aibot.strategy.ml_invest.service.impl;

import com.chicu.aibot.exchange.client.ExchangeClient;
import com.chicu.aibot.exchange.client.ExchangeClientFactory;
import com.chicu.aibot.exchange.service.ExchangeSettingsService;
import com.chicu.aibot.python.PythonInferenceService;
import com.chicu.aibot.strategy.ml_invest.service.MlDataPipelineService;
import com.chicu.aibot.strategy.service.CandleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ML-пайплайн без MarketLiveService:
 * - Универсум берём через ExchangeClient.fetchPopularSymbols()
 * - Свечи берём через CandleService.getCandles(...)
 * - Обучение/инференс — через PythonInferenceService
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MlDataPipelineServiceImpl implements MlDataPipelineService {

    private final ExchangeClientFactory clientFactory;
    private final ExchangeSettingsService settingsService;
    private final CandleService candleService;
    private final PythonInferenceService py;

    @Override
    public List<String> pickUniverse(Long chatId, String timeframe, int universeSize, BigDecimal min24hQuoteVolume) {
        var settings = settingsService.getOrCreate(chatId);
        ExchangeClient client = clientFactory.getClient(settings.getExchange());

        List<String> popular = client.fetchPopularSymbols();
        if (popular == null || popular.isEmpty()) {
            log.warn("[ML] fetchPopularSymbols вернул пусто — fallback на BTCUSDT");
            return List.of("BTCUSDT");
        }

        List<String> usdt = popular.stream()
                .filter(s -> s != null && s.endsWith("USDT"))
                .toList();

        List<String> universe = (usdt.isEmpty() ? popular : usdt).stream()
                .distinct()
                .limit(Math.max(1, universeSize))
                .collect(Collectors.toList());

        log.info("[ML] pickUniverse chatId={} exchange={} -> {} (size={})",
                chatId, settings.getExchange(), universe, universe.size());
        return universe;
    }

    @Override
    public Dataset buildTrainingDataset(Long chatId, List<String> symbols, String timeframe, int windowDays) {
        try {
            Path tmp = Files.createTempFile("ml_ds_", ".csv");
            int rows = 0;

            try (var w = Files.newBufferedWriter(tmp)) {
                w.write("symbol,timestamp,open,high,low,close,volume,rsi,ema12,ema26,macd,atr,label\n");

                Instant from = Instant.now().minus(Duration.ofDays(Math.max(windowDays, 1)));
                for (String sym : symbols) {
                    var candles = candleService.getCandles(chatId, sym, timeframe, 5000);
                    if (candles == null || candles.isEmpty()) continue;

                    var filtered = candles.stream()
                            .filter(c -> c.getOpenTime() != null && !c.getOpenTime().isBefore(from))
                            .sorted(Comparator.comparing(c -> c.getOpenTime()))
                            .toList();

                    var feats = IndicatorCalc.computeAll(filtered);
                    for (var f : feats) {
                        w.write(String.format(
                                Locale.US,
                                "%s,%d,%s,%s,%s,%s,%s,%.6f,%.6f,%.6f,%.6f,%.6f,%s%n",
                                sym,
                                f.ts().toEpochMilli(),
                                f.open().toPlainString(),
                                f.high().toPlainString(),
                                f.low().toPlainString(),
                                f.close().toPlainString(),
                                f.volume().toPlainString(),
                                f.rsi(), f.ema12(), f.ema26(), f.macd(), f.atr(),
                                "" // label пустая — train-скрипт сгенерит сам
                        ));
                        rows++;
                    }
                }
            }

            return new Dataset(symbols, timeframe, rows, 12, tmp);
        } catch (Exception e) {
            throw new RuntimeException("buildTrainingDataset failed", e);
        }
    }

    /**
     * Запуск обучения (через PythonInferenceService)
     */
    @Override
    public String trainIfNeeded(Long chatId, Dataset dataset, Duration retrainIfOlderThan, boolean force) {
        py.trainIfNeeded("ml_invest", chatId, dataset.timeframe, dataset.file.toString(), retrainIfOlderThan, force);
        return dataset.file.toString(); // ✅ теперь просто возвращаем путь к датасету
    }

    /**
     * Предсказание вероятностей BUY/SELL/HOLD
     */
    @Override
    public Map<String, Probabilities> predict(Long chatId, String modelRef, List<String> symbols, String timeframe) {
        try {
            // mini-CSV: последняя строка фич по каждому символу
            Path tmp = Files.createTempFile("ml_inf_", ".csv");
            try (var w = Files.newBufferedWriter(tmp)) {
                w.write("symbol,timestamp,open,high,low,close,volume,rsi,ema12,ema26,macd,atr\n");
                for (String sym : symbols) {
                    var candles = candleService.getCandles(chatId, sym, timeframe, 300);
                    if (candles == null || candles.isEmpty()) continue;

                    var feats = IndicatorCalc.computeAll(candles);
                    if (feats.isEmpty()) continue;

                    var f = feats.get(feats.size() - 1);
                    w.write(String.format(
                            Locale.US,
                            "%s,%d,%s,%s,%s,%s,%s,%.6f,%.6f,%.6f,%.6f,%.6f%n",
                            sym,
                            f.ts().toEpochMilli(),
                            f.open().toPlainString(),
                            f.high().toPlainString(),
                            f.low().toPlainString(),
                            f.close().toPlainString(),
                            f.volume().toPlainString(),
                            f.rsi(), f.ema12(), f.ema26(), f.macd(), f.atr()
                    ));
                }
            }

            Map<String, Double> raw = py.predictProba("ml_invest", chatId, modelRef, tmp.toString());
            Map<String, Probabilities> out = new HashMap<>();

            // ✅ корректное извлечение buy/sell/hold
            double buy = raw.getOrDefault("BUY", 0.0);
            double sell = raw.getOrDefault("SELL", 0.0);
            double hold = raw.getOrDefault("HOLD", 1.0);

            // Для всех символов одинаковые вероятности, если Python вернул общие
            for (String sym : symbols) {
                out.put(sym, new Probabilities(buy, sell, hold));
            }

            return out;
        } catch (Exception e) {
            throw new RuntimeException("predict failed", e);
        }
    }

    /* ====================== helpers / заглушки индикаторов ====================== */

    static class IndicatorCalc {
        record Row(Instant ts, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close,
                   BigDecimal volume, double rsi, double ema12, double ema26, double macd, double atr) {}

        static List<Row> computeAll(List<com.chicu.aibot.strategy.model.Candle> c) {
            List<Row> out = new ArrayList<>(c.size());
            double ema12 = 0.0, ema26 = 0.0, macd = 0.0, atr = 0.0;
            boolean first = true;

            for (var k : c.stream().sorted(Comparator.comparing(com.chicu.aibot.strategy.model.Candle::getOpenTime)).toList()) {
                var ts = k.getOpenTime() == null ? Instant.EPOCH : k.getOpenTime();
                var o = nz(k.getOpen());
                var h = nz(k.getHigh());
                var l = nz(k.getLow());
                var cl = nz(k.getClose());
                var v = nz(k.getVolume());

                double closeD = cl.doubleValue();

                if (first) {
                    ema12 = closeD;
                    ema26 = closeD;
                    atr = h.subtract(l).abs().doubleValue();
                    first = false;
                } else {
                    ema12 = ema(closeD, ema12, 12);
                    ema26 = ema(closeD, ema26, 26);
                    macd = ema12 - ema26;
                    double tr = Math.max(h.subtract(l).abs().doubleValue(), Math.abs(closeD - ema12));
                    atr = atr * 0.9 + tr * 0.1;
                }

                double rsi = 50.0; // заглушка RSI

                out.add(new Row(ts, o, h, l, cl, v, rsi, ema12, ema26, macd, atr));
            }
            return out;
        }

        private static double ema(double price, double prevEma, int period) {
            double k = 2.0 / (period + 1.0);
            return prevEma + k * (price - prevEma);
        }

        private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    }
}
