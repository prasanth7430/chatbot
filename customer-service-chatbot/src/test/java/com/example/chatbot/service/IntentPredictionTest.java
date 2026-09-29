package com.example.chatbot.service;

import com.example.chatbot.model.ChatResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class IntentPredictionTest {

    @Autowired
    private ChatBotService chatBotService;

    private String newSessionId() {
        return "test-" + UUID.randomUUID();
    }

    @Test
    @DisplayName("Should correctly classify 'greeting' intent")
    void testGreetingIntent() {
        ChatResponse res = chatBotService.handle(newSessionId(), "Hello there, good morning!");
        assertEquals("greeting", res.intent());
        assertTrue(res.confidence() >= 0.45);
        assertNotNull(res.response());
    }

    @Test
    @DisplayName("Should correctly classify 'order_status' intent")
    void testOrderStatusIntent() {
        ChatResponse res = chatBotService.handle(newSessionId(), "Where is my package #12345?");
        assertEquals("order_status", res.intent());
        assertTrue(res.confidence() >= 0.45);
        assertTrue(res.response().contains("#12345"));
    }

    @Test
    @DisplayName("Should correctly classify 'cancel_order' intent")
    void testCancelOrderIntent() {
        ChatResponse res = chatBotService.handle(newSessionId(), "I want to cancel order ORD-1234 right now");
        assertEquals("cancel_order", res.intent());
        assertTrue(res.confidence() >= 0.45);
        assertTrue(res.response().contains("ORD-1234"));
    }

    @Test
    @DisplayName("Should correctly classify 'refund_request' intent")
    void testRefundRequestIntent() {
        ChatResponse res = chatBotService.handle(newSessionId(), "I want a refund and my money back");
        assertEquals("refund_request", res.intent());
        assertTrue(res.confidence() >= 0.45);
        assertNotNull(res.response());
    }

    @Test
    @DisplayName("Should correctly classify 'shipping_info' intent")
    void testShippingInfoIntent() {
        ChatResponse res = chatBotService.handle(newSessionId(), "What are your standard delivery shipping options and rates?");
        assertEquals("shipping_info", res.intent());
        assertTrue(res.confidence() >= 0.45);
        assertNotNull(res.response());
    }

    @Test
    @DisplayName("Should correctly classify 'business_hours' intent")
    void testBusinessHoursIntent() {
        ChatResponse res = chatBotService.handle(newSessionId(), "What time do your customer service reps open and close?");
        assertEquals("business_hours", res.intent());
        assertTrue(res.confidence() >= 0.45);
        assertNotNull(res.response());
    }

    @Test
    @DisplayName("Should correctly handle 'contact_human_agent' intent")
    void testContactHumanAgentIntent() {
        ChatResponse res = chatBotService.handle(newSessionId(), "I need to talk to a human agent please");
        assertEquals("contact_human_agent", res.intent());
        assertTrue(res.confidence() >= 0.45);
        assertNotNull(res.response());
    }

    @Test
    @DisplayName("Should correctly classify 'account_help' intent")
    void testAccountHelpIntent() {
        ChatResponse res = chatBotService.handle(newSessionId(), "I forgot my password and cannot log into my account");
        assertEquals("account_help", res.intent());
        assertTrue(res.confidence() >= 0.45);
        assertNotNull(res.response());
    }

    @Test
    @DisplayName("Gibberish text must result in fallback intent")
    void testGibberishFallback() {
        ChatResponse res = chatBotService.handle(newSessionId(), "asdf qwerty zxcv blarg1239");
        assertEquals("fallback", res.intent());
        assertFalse(res.suggestions().isEmpty(), "Fallback must include topic suggestions");
    }
}
