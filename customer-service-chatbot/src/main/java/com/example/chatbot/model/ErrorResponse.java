package com.example.chatbot.model;

/**
 * Standard API error response payload.
 *
 * @param error   Error category or HTTP status name.
 * @param message Descriptive error message.
 */
public record ErrorResponse(
        String error,
        String message
) {
}
