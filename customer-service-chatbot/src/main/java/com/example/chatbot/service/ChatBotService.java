package com.example.chatbot.service;

import com.example.chatbot.config.ChatbotProperties;
import com.example.chatbot.model.*;
import com.example.chatbot.nlp.IntentClassifier;
import com.example.chatbot.nlp.ModelEvaluator;
import com.example.chatbot.nlp.TextPreprocessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentSample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Core customer service chatbot orchestrator.
 * Coordinates NLP classification, rule processing, order entity extraction,
 * session context tracking, and response generation.
 */
@Service
public class ChatBotService {

    private static final Logger log = LoggerFactory.getLogger(ChatBotService.class);

    private static final List<String> DEFAULT_FALLBACK_SUGGESTIONS = List.of(
            "Track my order",
            "Return an item",
            "Speak to a live agent"
    );

    private final ChatbotProperties properties;
    private final TextPreprocessor textPreprocessor;
    private final IntentClassifier intentClassifier;
    private final ModelEvaluator modelEvaluator;
    private final RuleEngine ruleEngine;
    private final OrderIdExtractor orderIdExtractor;
    private final SessionContextStore sessionContextStore;
    private final ObjectMapper objectMapper;

    private IntentsData intentsData;
    private final Map<String, Intent> intentMap = new HashMap<>();
    private final Random random = new Random();
    private EvaluationReport evaluationReport;

    public ChatBotService(
            ChatbotProperties properties,
            TextPreprocessor textPreprocessor,
            IntentClassifier intentClassifier,
            ModelEvaluator modelEvaluator,
            RuleEngine ruleEngine,
            OrderIdExtractor orderIdExtractor,
            SessionContextStore sessionContextStore,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.textPreprocessor = textPreprocessor;
        this.intentClassifier = intentClassifier;
        this.modelEvaluator = modelEvaluator;
        this.ruleEngine = ruleEngine;
        this.orderIdExtractor = orderIdExtractor;
        this.sessionContextStore = sessionContextStore;
        this.objectMapper = objectMapper;
    }

    /**
     * Initializes the chatbot service:
     * 1. Loads and parses intents.json from classpath
     * 2. Evaluates model performance via stratified 80/20 train/test split
     * 3. Retrains on the complete dataset for production and serializes the model
     */
    @PostConstruct
    public void init() {
        loadIntentsDataset();
        trainAndEvaluate();
    }

    /**
     * Loads intents.json dataset from classpath.
     */
    private void loadIntentsDataset() {
        try {
            ClassPathResource resource = new ClassPathResource("intents.json");
            if (!resource.exists()) {
                throw new IllegalStateException("CRITICAL: 'intents.json' not found on the classpath!");
            }

            try (InputStream is = resource.getInputStream()) {
                this.intentsData = objectMapper.readValue(is, IntentsData.class);
            }

            if (intentsData == null || intentsData.intents() == null || intentsData.intents().isEmpty()) {
                throw new IllegalStateException("CRITICAL: 'intents.json' is empty or invalid!");
            }

            for (Intent intent : intentsData.intents()) {
                intentMap.put(intent.tag(), intent);
            }

            log.info("Loaded {} intents and {} fallback responses from intents.json",
                    intentMap.size(), intentsData.fallback().size());
        } catch (IOException e) {
            log.error("Failed to load or parse intents.json: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to load or parse intents.json", e);
        }
    }

    /**
     * Executes the training and evaluation workflow:
     * - Stratified 80/20 split
     * - Train evaluation model on 80% & score on 20%
     * - Export confusion matrix CSV
     * - Retrain production model on 100% full dataset & save
     */
    public synchronized void trainAndEvaluate() {
        Map<String, List<DocumentSample>> samplesByIntent = modelEvaluator.buildSamplesByIntent(intentsData);
        List<String> labels = new ArrayList<>(samplesByIntent.keySet());

        // Stratified 80/20 split
        List<DocumentSample>[] split = modelEvaluator.stratifiedSplit(samplesByIntent, properties.getSeed());
        List<DocumentSample> trainSamples = split[0];
        List<DocumentSample> testSamples = split[1];

        log.info("Dataset partitioned: {} training samples, {} held-out test samples across {} intents",
                trainSamples.size(), testSamples.size(), labels.size());

        // Train on 80% and evaluate
        DoccatModel evalModel = intentClassifier.train(trainSamples, 120, 0);
        this.evaluationReport = modelEvaluator.evaluate(evalModel, testSamples, labels, properties.getConfusionMatrixPath());

        // Retrain on 100% of samples for production serving
        List<DocumentSample> allSamples = modelEvaluator.flattenSamples(samplesByIntent);
        log.info("Retraining production DoccatModel on complete dataset ({} samples)...", allSamples.size());
        DoccatModel prodModel = intentClassifier.train(allSamples, 120, 0);
        intentClassifier.setModel(prodModel);

        // Save model
        if (properties.getModelPath() != null && !properties.getModelPath().isBlank()) {
            intentClassifier.saveModel(prodModel, properties.getModelPath());
        }
    }

    /**
     * Processes a user chat message within a session.
     *
     * @param sessionId The conversational session ID.
     * @param message   The raw text message sent by the user.
     * @return ChatResponse containing intent, confidence, response text, and suggestions.
     */
    public ChatResponse handle(String sessionId, String message) {
        if (message == null || message.isBlank()) {
            return new ChatResponse("fallback", 0.0, getFallbackResponse(), DEFAULT_FALLBACK_SUGGESTIONS);
        }

        // 1. Session context retrieval
        Optional<SessionContextStore.SessionContext> existingContextOpt = sessionContextStore.get(sessionId);
        String sessionLastOrderId = existingContextOpt.map(SessionContextStore.SessionContext::lastOrderId).orElse(null);
        String sessionLastIntent = existingContextOpt.map(SessionContextStore.SessionContext::lastIntent).orElse(null);

        // 2. Order ID extraction from current message
        Optional<String> extractedOrderIdOpt = orderIdExtractor.extractOrderId(message);
        String currentOrderId = extractedOrderIdOpt.orElse(sessionLastOrderId);

        // 3. Rule Engine evaluation (e.g. human agent request)
        Optional<String> ruleIntentOpt = ruleEngine.evaluateRule(message);

        String recognizedIntent;
        double confidence;

        if (ruleIntentOpt.isPresent()) {
            recognizedIntent = ruleIntentOpt.get();
            confidence = 1.0;
            log.info("Session {} - Message matched deterministic rule: {}", sessionId, recognizedIntent);
        } else {
            // Check if user simply provided an order ID in response to a prompt for order ID
            boolean isJustOrderId = extractedOrderIdOpt.isPresent()
                    && textPreprocessor.cleanText(message).replaceAll("[0-9#ord\\-]", "").trim().isEmpty();

            if (isJustOrderId && sessionLastIntent != null && isOrderRelatedIntent(sessionLastIntent)) {
                recognizedIntent = sessionLastIntent;
                confidence = 0.95;
                log.info("Session {} - Follow-up order ID provided for previous intent: {}", sessionId, recognizedIntent);
            } else {
                // Statistical NLP classification
                ClassificationResult classification = intentClassifier.classify(message);
                recognizedIntent = classification.bestCategory();
                confidence = classification.confidence();
                log.info("Session {} - Classified intent: {} (confidence: {})", sessionId, recognizedIntent, confidence);
            }
        }

        // 4. Low-confidence fallback check
        if (confidence < properties.getConfidenceThreshold() || !intentMap.containsKey(recognizedIntent)) {
            log.info("Session {} - Low confidence ({}) below threshold ({}), returning fallback",
                    sessionId, confidence, properties.getConfidenceThreshold());
            return new ChatResponse(
                    "fallback",
                    confidence,
                    getFallbackResponse(),
                    DEFAULT_FALLBACK_SUGGESTIONS
            );
        }

        // 5. Handling Order ID requirement for order_status and cancel_order
        if (isOrderRelatedIntent(recognizedIntent)) {
            if (currentOrderId == null || currentOrderId.isBlank()) {
                sessionContextStore.update(sessionId, recognizedIntent, null);
                return new ChatResponse(
                        recognizedIntent,
                        confidence,
                        "Could you please provide your order ID (for example, #12345 or ORD-9921) so I can look that up for you?",
                        List.of("My order is #12345", "Check ORD-4321", "Talk to a live agent")
                );
            }
        }

        // 6. Select candidate response and fill {order_id}
        Intent intent = intentMap.get(recognizedIntent);
        String rawResponse = selectRandomResponse(intent.responses());
        String finalResponse = fillPlaceholders(rawResponse, currentOrderId);

        // 7. Update session context
        sessionContextStore.update(sessionId, recognizedIntent, currentOrderId);

        // 8. Generate follow-up suggestions
        List<String> suggestions = generateSuggestions(recognizedIntent);

        return new ChatResponse(recognizedIntent, confidence, finalResponse, suggestions);
    }

    private boolean isOrderRelatedIntent(String intentTag) {
        return "order_status".equals(intentTag) || "cancel_order".equals(intentTag);
    }

    private String selectRandomResponse(List<String> responses) {
        if (responses == null || responses.isEmpty()) {
            return "How else may I help you today?";
        }
        return responses.get(random.nextInt(responses.size()));
    }

    private String getFallbackResponse() {
        if (intentsData == null || intentsData.fallback() == null || intentsData.fallback().isEmpty()) {
            return "I apologize, but I could not understand your request. Could you please rephrase?";
        }
        return intentsData.fallback().get(random.nextInt(intentsData.fallback().size()));
    }

    private String fillPlaceholders(String template, String orderId) {
        String effectiveId = (orderId != null && !orderId.isBlank()) ? orderId : "your recent order";
        return template.replace("{order_id}", effectiveId);
    }

    private List<String> generateSuggestions(String intentTag) {
        return switch (intentTag) {
            case "greeting" -> List.of("Track my order", "What are your shipping rates?", "What is your return policy?");
            case "order_status" -> List.of("When will it arrive?", "Cancel this order", "Talk to a live agent");
            case "cancel_order" -> List.of("When will I get my refund?", "What is your return policy?", "Speak to a representative");
            case "refund_request" -> List.of("What is your return policy?", "Payment issue help", "Speak to a live person");
            case "return_policy" -> List.of("How do I initiate a return?", "Shipping information", "Talk to an agent");
            case "shipping_info" -> List.of("Track my package", "Do you offer express delivery?", "Return policy");
            case "payment_issue" -> List.of("Which payment methods are accepted?", "Talk to a live agent", "Check order status");
            case "change_address" -> List.of("Check order status", "Cancel my order", "Speak to support");
            case "product_availability" -> List.of("When will it be restocked?", "Shipping options", "Talk to an agent");
            case "contact_human_agent" -> List.of("What are your business hours?", "Track my order", "File a complaint");
            case "business_hours" -> List.of("Speak to an agent", "What is your return policy?", "Track my delivery");
            case "account_help" -> List.of("Reset my password", "Talk to a human agent", "Payment issue");
            case "complaint" -> List.of("Connect me to a live agent", "Check order status", "Refund status");
            case "goodbye", "thanks" -> List.of("I have another question", "Track an order", "Check business hours");
            default -> DEFAULT_FALLBACK_SUGGESTIONS;
        };
    }

    /**
     * Returns the list of all defined intent tags.
     */
    public List<String> getIntentTags() {
        return new ArrayList<>(intentMap.keySet());
    }

    public EvaluationReport getEvaluationReport() {
        return evaluationReport;
    }
}
