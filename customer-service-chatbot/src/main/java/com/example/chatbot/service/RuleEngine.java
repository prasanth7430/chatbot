package com.example.chatbot.service;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Deterministic rule engine that intercepts high-priority intents
 * (e.g., immediate human agent handoff) before statistical NLP classification.
 */
@Component
public class RuleEngine {

    // Regex matching explicit requests to connect with a human agent or representative
    private static final Pattern HUMAN_AGENT_PATTERN = Pattern.compile(
            "(?i)\\b(human|agent|representative|rep|operator|real person|talk to a person|speak with someone|live support|live agent)\\b"
    );

    /**
     * Evaluates whether the raw user input triggers a deterministic rule.
     *
     * @param message Raw message string from the user.
     * @return Optional containing the deterministic intent tag, or empty if no rule applies.
     */
    public Optional<String> evaluateRule(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }

        if (HUMAN_AGENT_PATTERN.matcher(message).find()) {
            return Optional.of("contact_human_agent");
        }

        return Optional.empty();
    }
}
