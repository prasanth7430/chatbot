package com.example.chatbot.model;

import java.util.List;

/**
 * Representation of a conversational intent definition.
 *
 * @param tag       The unique intent tag/label.
 * @param patterns  Training and recognition patterns/utterances.
 * @param responses Candidate responses to select from.
 */
public record Intent(
        String tag,
        List<String> patterns,
        List<String> responses
) {
    public Intent {
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("Intent tag cannot be null or blank");
        }
        patterns = patterns == null ? List.of() : List.copyOf(patterns);
        responses = responses == null ? List.of() : List.copyOf(responses);
    }
}
