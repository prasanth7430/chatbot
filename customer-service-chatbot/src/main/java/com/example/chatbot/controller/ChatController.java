package com.example.chatbot.controller;

import com.example.chatbot.model.ChatRequest;
import com.example.chatbot.model.ChatResponse;
import com.example.chatbot.service.ChatBotService;
import com.example.chatbot.service.SessionContextStore;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API Controller exposing chatbot interaction, intent exploration,
 * and session management endpoints.
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatBotService chatBotService;
    private final SessionContextStore sessionContextStore;

    public ChatController(ChatBotService chatBotService, SessionContextStore sessionContextStore) {
        this.chatBotService = chatBotService;
        this.sessionContextStore = sessionContextStore;
    }

    /**
     * Handles an incoming chat request from a user.
     *
     * @param request Validated ChatRequest payload containing sessionId and message.
     * @return ChatResponse with recognized intent, confidence, response text, and suggestions.
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        ChatResponse response = chatBotService.handle(request.sessionId(), request.message());
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves all available conversational intent tags.
     *
     * @return List of intent tags.
     */
    @GetMapping("/intents")
    public ResponseEntity<List<String>> getIntents() {
        return ResponseEntity.ok(chatBotService.getIntentTags());
    }

    /**
     * Clears conversational memory and history for a given session.
     * Supports either JSON body {"sessionId": "..."} or query parameter ?sessionId=...
     *
     * @param body Optional request body containing sessionId.
     * @param paramSessionId Optional query parameter sessionId.
     * @return Confirmation status map.
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> reset(
            @RequestBody(required = false) Map<String, String> body,
            @RequestParam(name = "sessionId", required = false) String paramSessionId
    ) {
        String targetSessionId = null;
        if (body != null && body.containsKey("sessionId")) {
            targetSessionId = body.get("sessionId");
        } else if (paramSessionId != null && !paramSessionId.isBlank()) {
            targetSessionId = paramSessionId;
        }

        if (targetSessionId != null && !targetSessionId.isBlank()) {
            sessionContextStore.clear(targetSessionId);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Session " + targetSessionId + " cleared successfully"
            ));
        } else {
            sessionContextStore.clearAll();
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "All active sessions cleared successfully"
            ));
        }
    }
}
