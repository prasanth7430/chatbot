package com.example.chatbot.model;

import java.util.List;

/**
 * Encapsulates the dataset parsed from intents.json.
 *
 * @param intents  The list of defined intents.
 * @param fallback The list of fallback responses when confidence is low.
 */
public record IntentsData(
        List<Intent> intents,
        List<String> fallback
) {
    public IntentsData {
        intents = intents == null ? List.of() : List.copyOf(intents);
        fallback = fallback == null ? List.of() : List.copyOf(fallback);
    }
}
