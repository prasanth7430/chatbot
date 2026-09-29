package com.example.chatbot.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Incoming chat request payload.
 *
 * @param sessionId Unique session identifier.
 * @param message   User input message.
 */
public record ChatRequest(
        @NotBlank(message = "sessionId is required and cannot be blank")
        String sessionId,

        @NotBlank(message = "message is required and cannot be blank")
        @Size(max = 500, message = "message cannot exceed 500 characters")
        String message
) {
}
