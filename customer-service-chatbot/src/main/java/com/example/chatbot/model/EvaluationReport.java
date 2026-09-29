package com.example.chatbot.model;

import java.util.List;
import java.util.Map;

/**
 * Report containing model evaluation metrics including accuracy, precision, recall,
 * F1 score, and the confusion matrix.
 */
public record EvaluationReport(
        double accuracy,
        int totalSamples,
        int correctSamples,
        Map<String, Double> precisionMap,
        Map<String, Double> recallMap,
        Map<String, Double> f1Map,
        double macroPrecision,
        double macroRecall,
        double macroF1,
        List<String> labels,
        int[][] confusionMatrix
) {
}
