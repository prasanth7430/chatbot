package com.example.chatbot.nlp;

import com.example.chatbot.model.EvaluationReport;
import com.example.chatbot.model.Intent;
import com.example.chatbot.model.IntentsData;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.doccat.DocumentSample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Handles dataset partitioning (stratified 80/20 train/test split with fixed seed),
 * cross-evaluation, metric computation (accuracy, per-intent precision, recall, F1, macro-averages),
 * and confusion matrix CSV report generation.
 */
@Component
public class ModelEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ModelEvaluator.class);

    private final TextPreprocessor textPreprocessor;
    private final IntentClassifier intentClassifier;

    public ModelEvaluator(TextPreprocessor textPreprocessor, IntentClassifier intentClassifier) {
        this.textPreprocessor = textPreprocessor;
        this.intentClassifier = intentClassifier;
    }

    /**
     * Converts raw intent patterns from IntentsData into DocumentSamples.
     *
     * @param intentsData Parsed intents dataset.
     * @return Map of intent tag to its list of DocumentSample instances.
     */
    public Map<String, List<DocumentSample>> buildSamplesByIntent(IntentsData intentsData) {
        Map<String, List<DocumentSample>> map = new LinkedHashMap<>();
        for (Intent intent : intentsData.intents()) {
            List<DocumentSample> samples = new ArrayList<>();
            for (String pattern : intent.patterns()) {
                String[] tokens = textPreprocessor.preprocessToArray(pattern);
                if (tokens.length > 0) {
                    samples.add(new DocumentSample(intent.tag(), tokens));
                }
            }
            map.put(intent.tag(), samples);
        }
        return map;
    }

    /**
     * Flattens all intent samples into a single list.
     *
     * @param samplesByIntent Map of tag to samples.
     * @return Combined list of DocumentSamples.
     */
    public List<DocumentSample> flattenSamples(Map<String, List<DocumentSample>> samplesByIntent) {
        List<DocumentSample> all = new ArrayList<>();
        for (List<DocumentSample> list : samplesByIntent.values()) {
            all.addAll(list);
        }
        return all;
    }

    /**
     * Performs a stratified 80/20 train/test split with a fixed seed.
     *
     * @param samplesByIntent Samples grouped by intent tag.
     * @param seed            Random seed for reproducibility.
     * @return Array containing [trainSamples, testSamples].
     */
    @SuppressWarnings("unchecked")
    public List<DocumentSample>[] stratifiedSplit(Map<String, List<DocumentSample>> samplesByIntent, long seed) {
        List<DocumentSample> train = new ArrayList<>();
        List<DocumentSample> test = new ArrayList<>();
        Random random = new Random(seed);

        for (Map.Entry<String, List<DocumentSample>> entry : samplesByIntent.entrySet()) {
            List<DocumentSample> list = new ArrayList<>(entry.getValue());
            Collections.shuffle(list, random);

            int total = list.size();
            int trainSize = (int) Math.round(total * 0.80);
            if (trainSize == total && total > 1) {
                trainSize = total - 1; // Guarantee at least 1 test sample per intent
            }

            for (int i = 0; i < total; i++) {
                if (i < trainSize) {
                    train.add(list.get(i));
                } else {
                    test.add(list.get(i));
                }
            }
        }

        // Shuffle combined sets
        Collections.shuffle(train, random);
        Collections.shuffle(test, random);

        return new List[]{train, test};
    }

    /**
     * Evaluates a trained DoccatModel on the test dataset and outputs a comprehensive evaluation report.
     *
     * @param model               The DoccatModel to evaluate.
     * @param testSamples         Held-out test samples.
     * @param labels              Distinct intent labels.
     * @param confusionMatrixPath File path to write the confusion matrix CSV report.
     * @return EvaluationReport with all metrics.
     */
    public EvaluationReport evaluate(DoccatModel model, List<DocumentSample> testSamples,
                                     List<String> labels, String confusionMatrixPath) {
        DocumentCategorizerME categorizer = new DocumentCategorizerME(model);

        Map<String, Integer> labelToIndex = new HashMap<>();
        for (int i = 0; i < labels.size(); i++) {
            labelToIndex.put(labels.get(i), i);
        }

        int numLabels = labels.size();
        int[][] confusionMatrix = new int[numLabels][numLabels];

        int total = testSamples.size();
        int correct = 0;

        for (DocumentSample sample : testSamples) {
            String actual = sample.getCategory();
            double[] outcomes = categorizer.categorize(sample.getText());
            String predicted = categorizer.getBestCategory(outcomes);

            Integer actualIdx = labelToIndex.get(actual);
            Integer predIdx = labelToIndex.get(predicted);

            if (actualIdx != null && predIdx != null) {
                confusionMatrix[actualIdx][predIdx]++;
            }

            if (actual.equals(predicted)) {
                correct++;
            } else {
                log.warn("EVAL MISMATCH: actual='{}', predicted='{}', text={}",
                        actual, predicted, Arrays.toString(sample.getText()));
            }
        }

        double accuracy = total > 0 ? (double) correct / total : 0.0;

        Map<String, Double> precisionMap = new LinkedHashMap<>();
        Map<String, Double> recallMap = new LinkedHashMap<>();
        Map<String, Double> f1Map = new LinkedHashMap<>();

        double sumPrecision = 0.0;
        double sumRecall = 0.0;
        double sumF1 = 0.0;

        for (int i = 0; i < numLabels; i++) {
            String label = labels.get(i);
            int tp = confusionMatrix[i][i];

            int actualTotal = 0;
            for (int col = 0; col < numLabels; col++) {
                actualTotal += confusionMatrix[i][col];
            }

            int predictedTotal = 0;
            for (int row = 0; row < numLabels; row++) {
                predictedTotal += confusionMatrix[row][i];
            }

            double precision = predictedTotal > 0 ? (double) tp / predictedTotal : (actualTotal == 0 ? 1.0 : 0.0);
            double recall = actualTotal > 0 ? (double) tp / actualTotal : 1.0;
            double f1 = (precision + recall) > 0 ? (2.0 * precision * recall) / (precision + recall) : 0.0;

            precisionMap.put(label, precision);
            recallMap.put(label, recall);
            f1Map.put(label, f1);

            sumPrecision += precision;
            sumRecall += recall;
            sumF1 += f1;
        }

        double macroPrecision = numLabels > 0 ? sumPrecision / numLabels : 0.0;
        double macroRecall = numLabels > 0 ? sumRecall / numLabels : 0.0;
        double macroF1 = numLabels > 0 ? sumF1 / numLabels : 0.0;

        logEvaluationMetrics(accuracy, total, correct, macroPrecision, macroRecall, macroF1,
                labels, precisionMap, recallMap, f1Map);

        if (confusionMatrixPath != null && !confusionMatrixPath.isBlank()) {
            writeConfusionMatrixCsv(labels, confusionMatrix, confusionMatrixPath);
        }

        return new EvaluationReport(
                accuracy, total, correct,
                precisionMap, recallMap, f1Map,
                macroPrecision, macroRecall, macroF1,
                labels, confusionMatrix
        );
    }

    private void logEvaluationMetrics(double accuracy, int total, int correct,
                                      double macroPrecision, double macroRecall, double macroF1,
                                      List<String> labels,
                                      Map<String, Double> precisionMap,
                                      Map<String, Double> recallMap,
                                      Map<String, Double> f1Map) {
        log.info("================ MODEL EVALUATION SUMMARY ================");
        log.info("Accuracy: {}% ({}/{} correct)", String.format(Locale.ROOT, "%.2f", accuracy * 100), correct, total);
        log.info("Macro Precision: {}", String.format(Locale.ROOT, "%.4f", macroPrecision));
        log.info("Macro Recall:    {}", String.format(Locale.ROOT, "%.4f", macroRecall));
        log.info("Macro F1-Score:  {}", String.format(Locale.ROOT, "%.4f", macroF1));
        log.info("---------------- Per-Intent Breakdown --------------------");
        log.info(String.format("%-25s | %-10s | %-10s | %-10s", "Intent", "Precision", "Recall", "F1-Score"));
        log.info("----------------------------------------------------------");
        for (String label : labels) {
            log.info(String.format("%-25s | %-10.4f | %-10.4f | %-10.4f",
                    label,
                    precisionMap.getOrDefault(label, 0.0),
                    recallMap.getOrDefault(label, 0.0),
                    f1Map.getOrDefault(label, 0.0)));
        }
        log.info("==========================================================");
    }

    /**
     * Exports the confusion matrix into a well-structured CSV file.
     *
     * @param labels          List of intent labels.
     * @param matrix          The NxN confusion matrix counts.
     * @param destinationPath Target CSV file location.
     */
    public void writeConfusionMatrixCsv(List<String> labels, int[][] matrix, String destinationPath) {
        try {
            Path path = Paths.get(destinationPath);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }

            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                // Header
                writer.write("Actual \\ Predicted");
                for (String label : labels) {
                    writer.write("," + escapeCsv(label));
                }
                writer.write(",Total\n");

                // Rows
                for (int i = 0; i < labels.size(); i++) {
                    writer.write(escapeCsv(labels.get(i)));
                    int rowSum = 0;
                    for (int j = 0; j < labels.size(); j++) {
                        writer.write("," + matrix[i][j]);
                        rowSum += matrix[i][j];
                    }
                    writer.write("," + rowSum + "\n");
                }
            }
            log.info("Saved confusion matrix CSV to: {}", destinationPath);
        } catch (IOException e) {
            log.error("Failed to write confusion matrix to {}: {}", destinationPath, e.getMessage(), e);
        }
    }

    private String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
