package com.example.chatbot.model;

import java.util.Map;

/**
 * Result of intent classification containing the best intent tag, its confidence,
 * and a map of scores for all possible intents.
 *
 * @param bestCategory The highest scoring intent tag.
 * @param confidence   Probability/confidence of the highest scoring intent (0.0 to 1.0).
 * @param allScores    Map of all intent tags to their respective probabilities.
 */
public record ClassificationResult(
        String bestCategory,
        double confidence,
        Map<String, Double> allScores
) {
    public ClassificationResult {
        allScores = allScores == null ? Map.of() : Map.copyOf(allScores);
    }
}
