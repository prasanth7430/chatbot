package com.example.chatbot.model;

import java.util.List;

/**
 * Chatbot response payload.
 *
 * @param intent      The recognized intent tag (or "fallback").
 * @param confidence  Classification confidence score between 0.0 and 1.0.
 * @param response    The bot reply message text.
 * @param suggestions List of suggested follow-up prompts or actions.
 */
public record ChatResponse(
        String intent,
        double confidence,
        String response,
        List<String> suggestions
) {
    public ChatResponse {
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
    }
}
