package com.example.chatbot.nlp;

import com.example.chatbot.model.ClassificationResult;
import opennlp.tools.doccat.*;
import opennlp.tools.util.CollectionObjectStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Apache OpenNLP Maximum Entropy classifier for customer service conversational intents.
 * Uses a combination of Bag-of-Words and Bigram n-gram feature generators.
 */
@Component
public class IntentClassifier {

    private static final Logger log = LoggerFactory.getLogger(IntentClassifier.class);

    private final TextPreprocessor textPreprocessor;
    private volatile DoccatModel model;
    private volatile DocumentCategorizerME categorizer;

    public IntentClassifier(TextPreprocessor textPreprocessor) {
        this.textPreprocessor = textPreprocessor;
    }

    /**
     * Trains a DoccatModel on the provided document samples using Bag-of-Words and Bigram features.
     *
     * @param samples    List of training document samples.
     * @param iterations Number of training iterations.
     * @param cutoff     Minimum feature frequency cutoff (0 to retain all n-grams).
     * @return The trained DoccatModel.
     */
    public DoccatModel train(List<DocumentSample> samples, int iterations, int cutoff) {
        try {
            ObjectStream<DocumentSample> sampleStream = new CollectionObjectStream<>(samples);

            TrainingParameters params = new TrainingParameters();
            params.put(TrainingParameters.ITERATIONS_PARAM, iterations);
            params.put(TrainingParameters.CUTOFF_PARAM, cutoff);
            params.put(TrainingParameters.ALGORITHM_PARAM, "MAXENT");

            // Bag-of-words + Bigram feature generators
            FeatureGenerator[] featureGenerators = new FeatureGenerator[]{
                    new BagOfWordsFeatureGenerator(),
                    new NGramFeatureGenerator(2, 2)
            };
            DoccatFactory factory = new DoccatFactory(featureGenerators);

            DoccatModel trainedModel = DocumentCategorizerME.train("en", sampleStream, params, factory);
            log.info("Successfully trained DoccatModel with {} samples, {} iterations, cutoff {}",
                    samples.size(), iterations, cutoff);
            return trainedModel;
        } catch (IOException e) {
            log.error("Failed to train intent model: {}", e.getMessage(), e);
            throw new RuntimeException("Model training failed", e);
        }
    }

    /**
     * Sets the active model used for classifications.
     *
     * @param model Trained DoccatModel instance.
     */
    public synchronized void setModel(DoccatModel model) {
        this.model = Objects.requireNonNull(model, "Model cannot be null");
        this.categorizer = new DocumentCategorizerME(model);
    }

    /**
     * Classifies a preprocessed array of tokens into an intent category.
     *
     * @param tokens Preprocessed tokens.
     * @return ClassificationResult containing best intent tag, confidence, and all intent probabilities.
     */
    public ClassificationResult classifyTokens(String[] tokens) {
        if (categorizer == null) {
            throw new IllegalStateException("IntentClassifier model has not been initialized or trained yet.");
        }

        if (tokens == null || tokens.length == 0) {
            return new ClassificationResult("fallback", 0.0, Map.of());
        }

        double[] outcomes = categorizer.categorize(tokens);
        String bestCategory = categorizer.getBestCategory(outcomes);
        int bestIndex = categorizer.getIndex(bestCategory);
        double confidence = outcomes[bestIndex];

        Map<String, Double> allScores = new HashMap<>();
        for (int i = 0; i < categorizer.getNumberOfCategories(); i++) {
            String category = categorizer.getCategory(i);
            allScores.put(category, outcomes[i]);
        }

        return new ClassificationResult(bestCategory, confidence, allScores);
    }

    /**
     * Preprocesses raw input text and classifies it into an intent category.
     *
     * @param rawText Raw text entered by the user.
     * @return ClassificationResult with top intent and confidence.
     */
    public ClassificationResult classify(String rawText) {
        String[] tokens = textPreprocessor.preprocessToArray(rawText);
        return classifyTokens(tokens);
    }

    /**
     * Saves a DoccatModel to the designated filesystem path.
     *
     * @param modelToSave The model to persist.
     * @param destination The file path where the binary model will be stored.
     */
    public void saveModel(DoccatModel modelToSave, String destination) {
        try {
            Path path = Paths.get(destination);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(path))) {
                modelToSave.serialize(out);
            }
            log.info("Model serialized and saved to: {}", destination);
        } catch (IOException e) {
            log.error("Failed to save model to {}: {}", destination, e.getMessage(), e);
            throw new RuntimeException("Could not persist model to " + destination, e);
        }
    }

    /**
     * Loads a DoccatModel from the designated filesystem path.
     *
     * @param sourcePath File path to load the model from.
     * @return Loaded DoccatModel.
     */
    public DoccatModel loadModel(String sourcePath) {
        try {
            Path path = Paths.get(sourcePath);
            if (!Files.exists(path)) {
                throw new FileNotFoundException("Model file not found at " + sourcePath);
            }
            try (InputStream in = new BufferedInputStream(Files.newInputStream(path))) {
                DoccatModel loadedModel = new DoccatModel(in);
                log.info("Model loaded successfully from: {}", sourcePath);
                return loadedModel;
            }
        } catch (IOException e) {
            log.error("Failed to load model from {}: {}", sourcePath, e.getMessage(), e);
            throw new RuntimeException("Could not load model from " + sourcePath, e);
        }
    }

    public DoccatModel getModel() {
        return model;
    }
}
