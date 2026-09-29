package com.example.chatbot.service;

import com.example.chatbot.model.ChatResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ChatBotServiceMemoryTest {

    @Autowired
    private ChatBotService chatBotService;

    @Test
    @DisplayName("Follow-up messages should reuse previous order ID from session context")
    void testSessionMemoryFollowUp() {
        String sessionId = "memory-test-" + UUID.randomUUID();

        // 1st Turn: user asks about order status with specific ID
        ChatResponse res1 = chatBotService.handle(sessionId, "What is the status of my order #12345?");
        assertEquals("order_status", res1.intent());
        assertTrue(res1.response().contains("#12345"), "First response must reference #12345");

        // 2nd Turn: user asks a follow-up without repeating the order ID
        ChatResponse res2 = chatBotService.handle(sessionId, "I changed my mind stop my order");
        assertEquals("cancel_order", res2.intent());
        assertTrue(res2.response().contains("#12345"), "Second response must reuse #12345 from session memory");
    }

    @Test
    @DisplayName("Missing order ID should prompt user, and providing ID in follow-up should resolve intent")
    void testMissingOrderIdPromptAndResolution() {
        String sessionId = "prompt-test-" + UUID.randomUUID();

        // Turn 1: user asks order status without giving an ID
        ChatResponse res1 = chatBotService.handle(sessionId, "Can you track my package?");
        assertEquals("order_status", res1.intent());
        assertTrue(res1.response().toLowerCase().contains("order id"), "Bot should ask for the missing order ID");

        // Turn 2: user replies with just the order ID
        ChatResponse res2 = chatBotService.handle(sessionId, "#99432");
        assertEquals("order_status", res2.intent());
        assertTrue(res2.response().contains("#99432"), "Bot should resolve order status using the provided ID");
    }
}
