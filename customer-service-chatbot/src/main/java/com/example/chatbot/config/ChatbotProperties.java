package com.example.chatbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the chatbot application.
 */
@ConfigurationProperties(prefix = "chatbot")
public class ChatbotProperties {

    /**
     * Minimum confidence threshold required to accept a classified intent.
     * Below this threshold, fallback responses are triggered.
     */
    private double confidenceThreshold = 0.45;

    /**
     * Random seed used for reproducible stratified train/test dataset splits.
     */
    private long seed = 42L;

    /**
     * File path where the trained Apache OpenNLP DoccatModel binary is persisted.
     */
    private String modelPath = "models/intent-model.bin";

    /**
     * Path where the evaluation confusion matrix CSV report is exported.
     */
    private String confusionMatrixPath = "target/reports/confusion-matrix.csv";

    /**
     * Session context store configuration.
     */
    private Session session = new Session();

    public double getConfidenceThreshold() {
        return confidenceThreshold;
    }

    public void setConfidenceThreshold(double confidenceThreshold) {
        this.confidenceThreshold = confidenceThreshold;
    }

    public long getSeed() {
        return seed;
    }

    public void setSeed(long seed) {
        this.seed = seed;
    }

    public String getModelPath() {
        return modelPath;
    }

    public void setModelPath(String modelPath) {
        this.modelPath = modelPath;
    }

    public String getConfusionMatrixPath() {
        return confusionMatrixPath;
    }

    public void setConfusionMatrixPath(String confusionMatrixPath) {
        this.confusionMatrixPath = confusionMatrixPath;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public static class Session {
        private int maxEntries = 1000;
        private int ttlMinutes = 60;

        public int getMaxEntries() {
            return maxEntries;
        }

        public void setMaxEntries(int maxEntries) {
            this.maxEntries = maxEntries;
        }

        public int getTtlMinutes() {
            return ttlMinutes;
        }

        public void setTtlMinutes(int ttlMinutes) {
            this.ttlMinutes = ttlMinutes;
        }
    }
}
