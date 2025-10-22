package com.chicu.aibot.python.impl;

import com.chicu.aibot.python.PythonInferenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Безопасное взаимодействие с Python для ML Invest:
 * - обучение (train)
 * - предсказание (predict)
 *
 * Исправления:
 * 1) Больше НЕ создаёт "заглушку" ml_model.joblib (ломала загрузку модели в Python).
 * 2) Если train вернул "skipped/too_few_rows", корректно логируем и НЕ заявляем об обучении.
 * 3) Если модели нет, predictProba спокойно возвращает нули (BUY/SELL/HOLD=0) и НЕ вызывает python-скрипт.
 * 4) Логи не путают timeframe и путь к csv.
 */
@Slf4j
@Service
public class PythonInferenceServiceImpl implements PythonInferenceService {

    private static final Path PROJECT_ROOT = Path.of(System.getProperty("user.dir"));

    private static final Path PYTHON_EXECUTABLE =
            PROJECT_ROOT.resolve("python_ml/venv/Scripts/python.exe");

    private static final Path TRAIN_SCRIPT =
            PROJECT_ROOT.resolve("python_ml/ml_invest/train_ml_invest.py");

    private static final Path PREDICT_SCRIPT =
            PROJECT_ROOT.resolve("python_ml/ml_invest/predict_ml_invest.py");

    private static final Path DATASET_DIR =
            PROJECT_ROOT.resolve("python_ml/datasets");

    private static final Path MODEL_DIR =
            PROJECT_ROOT.resolve("python_ml/ml_invest");

    @Override
    public synchronized void trainIfNeeded(String datasetPath,
                                           Long chatId,
                                           String modelDir,
                                           String timeframe,
                                           Duration retrainPeriod,
                                           boolean force) {
        try {
            Files.createDirectories(DATASET_DIR);
            Files.createDirectories(MODEL_DIR);

            // dataset normalize
            Path dataset = Path.of(datasetPath);
            if (!dataset.isAbsolute()) {
                dataset = dataset.toString().endsWith(".csv")
                        ? DATASET_DIR.resolve(dataset)
                        : DATASET_DIR.resolve(dataset + ".csv");
            }

            // model dir normalize
            Path modelDirPath = Path.of(modelDir);
            if (!modelDirPath.isAbsolute()) {
                modelDirPath = MODEL_DIR;
            }
            Files.createDirectories(modelDirPath);

            // ensure dataset exists (header only)
            if (!Files.exists(dataset)) {
                log.warn("[ML] ⚠️ Dataset не найден: {} → создаю пустой шаблон", dataset);
                Files.writeString(dataset, "timestamp,open,high,low,close,volume,symbol\n");
            }

            List<String> cmd = new ArrayList<>(List.of(
                    PYTHON_EXECUTABLE.toString(),
                    TRAIN_SCRIPT.toString(),
                    "--dataset", dataset.toString(),
                    "--model_dir", modelDirPath.toString()
            ));
            if (force) cmd.add("--force");

            log.info("[ML] 🚀 trainIfNeeded → chatId={} tf={} dataset={}", chatId, timeframe, dataset);
            log.info("[ML] 🚀 Запуск Python: {}", cmd);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }

            int exit = process.waitFor();
            log.info("[ML] 🧠 Python output:\n{}", output);

            String status = extractStatus(output);

            if (exit == 0) {
                if ("skipped".equalsIgnoreCase(status) || "too_few_rows".equalsIgnoreCase(status)) {
                    log.warn("[ML] ⚠️ Обучение пропущено (нет/мало данных). Модель не обновлялась. chatId={}, tf={}", chatId, timeframe);
                } else {
                    log.info("[ML] ✅ Обучение завершено успешно (chatId={}, tf={})", chatId, timeframe);
                }
            } else {
                log.error("[ML] ❌ Ошибка обучения (exit={})", exit);
            }

        } catch (Exception e) {
            log.error("[ML] ❌ Ошибка trainIfNeeded", e);
        }
    }

    @Override
    public Map<String, Double> predictProba(String modelDir,
                                            Long chatId,
                                            String timeframe,
                                            String datasetPath) {
        try {
            Files.createDirectories(DATASET_DIR);
            Files.createDirectories(MODEL_DIR);

            // dataset normalize
            Path dataset = Path.of(datasetPath);
            if (!dataset.isAbsolute()) {
                dataset = dataset.toString().endsWith(".csv")
                        ? DATASET_DIR.resolve(dataset)
                        : DATASET_DIR.resolve(dataset + ".csv");
            }
            if (!Files.exists(dataset)) {
                log.warn("[ML] ⚠️ Dataset не найден при predictProba: {}", dataset);
                return defaultMap();
            }

            // model dir normalize
            Path modelDirPath = Path.of(modelDir);
            if (!modelDirPath.isAbsolute()) {
                modelDirPath = MODEL_DIR;
            }

            // проверить наличие валидной модели
            Path mainModel = modelDirPath.resolve("ml_model.joblib");
            Path altModel  = PROJECT_ROOT.resolve("ml_invest/ml_model.joblib");

            boolean hasMain = Files.exists(mainModel);
            boolean hasAlt  = Files.exists(altModel);

            if (!hasMain && hasAlt) {
                log.info("[ML] ⚙️ Использую модель из альтернативного пути: {}", altModel);
                modelDirPath = PROJECT_ROOT.resolve("ml_invest");
                hasMain = true;
            }

            if (!hasMain) {
                // Модели нет — НЕ запускаем python-predict
                log.warn("[ML] ⚠️ Модель не найдена ни в {}, ни в {} — возвращаю нули.", mainModel, altModel);
                return defaultMap();
            }

            List<String> cmd = new ArrayList<>(List.of(
                    PYTHON_EXECUTABLE.toString(),
                    PREDICT_SCRIPT.toString(),
                    "--model_dir", modelDirPath.toString(),
                    "--dataset", dataset.toString()
            ));

            log.info("[ML] 🔮 predictProba → chatId={} tf={} modelDir={} dataset={}",
                    chatId, timeframe, modelDirPath, dataset);
            log.info("[ML] 🔮 predictProba cmd={}", cmd);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }

            int exit = process.waitFor();
            log.info("[ML] 🧠 Python output:\n{}", output);

            if (exit != 0) {
                log.error("[ML] ❌ Ошибка predictProba (exit={}) — возвращаю нули", exit);
                return defaultMap();
            }

            Map<String, Double> probs = parseJsonOutput(output);
            if (probs.isEmpty()) {
                log.warn("[ML] ⚠️ Пустой результат — возвращаю нули");
                return defaultMap();
            }

            log.info("[ML] ✅ predictProba result: {}", probs);
            return probs;

        } catch (Exception e) {
            log.error("[ML] ❌ Ошибка predictProba", e);
            return defaultMap();
        }
    }

    /* ================= helpers ================= */

    private String extractStatus(String output) {
        try {
            String[] lines = output.split("\n");
            for (int i = lines.length - 1; i >= 0; i--) {
                String ln = lines[i].trim();
                if (ln.isEmpty()) continue;

                if (ln.startsWith("{") && ln.endsWith("}")) {
                    String s = ln.replaceAll("[{}\\\"]", "");
                    for (String pair : s.split(",")) {
                        String[] kv = pair.split(":");
                        if (kv.length == 2 && "status".equalsIgnoreCase(kv[0].trim())) {
                            return kv[1].trim();
                        }
                    }
                }
                if (!ln.contains("{") && !ln.contains("}") && !ln.contains(":") && ln.length() < 64) {
                    return ln;
                }
            }
        } catch (Exception ignore) {}
        return null;
    }

    private Map<String, Double> parseJsonOutput(String text) {
        Map<String, Double> res = new LinkedHashMap<>();
        try {
            String[] lines = text.trim().split("\n");
            String last = lines[lines.length - 1].trim();
            String json = last.startsWith("{") ? last : text.trim();

            json = json.replaceAll("[{}\" ]", "");
            for (String part : json.split(",")) {
                String[] kv = part.split(":");
                if (kv.length == 2) {
                    String key = kv[0].toUpperCase();
                    if ("BUY".equals(key) || "SELL".equals(key) || "HOLD".equals(key)) {
                        res.put(key, Double.parseDouble(kv[1]));
                    }
                }
            }
        } catch (Exception ignore) {}
        return res;
    }

    private Map<String, Double> defaultMap() {
        Map<String, Double> map = new LinkedHashMap<>();
        map.put("BUY", 0.0);
        map.put("SELL", 0.0);
        map.put("HOLD", 0.0);
        return map;
    }
}
