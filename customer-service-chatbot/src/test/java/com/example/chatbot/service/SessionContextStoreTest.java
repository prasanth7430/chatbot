package com.example.chatbot.service;

import com.example.chatbot.config.ChatbotProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SessionContextStoreTest {

    private SessionContextStore store;

    @BeforeEach
    void setUp() {
        ChatbotProperties props = new ChatbotProperties();
        props.getSession().setMaxEntries(10);
        props.getSession().setTtlMinutes(5);
        store = new SessionContextStore(props);
    }

    @Test
    @DisplayName("Should save and retrieve session context accurately")
    void testSessionContextSaveAndRetrieve() {
        String sessionId = "test-session-1";
        store.update(sessionId, "order_status", "#12345");

        Optional<SessionContextStore.SessionContext> contextOpt = store.get(sessionId);
        assertTrue(contextOpt.isPresent());
        assertEquals("order_status", contextOpt.get().lastIntent());
        assertEquals("#12345", contextOpt.get().lastOrderId());
    }

    @Test
    @DisplayName("Subsequent updates without an order ID should preserve the previously saved order ID")
    void testContextUpdatePreservesOrderId() {
        String sessionId = "test-session-2";
        store.update(sessionId, "order_status", "ORD-9911");

        // Next message: intent is shipping_info, no new orderId provided
        store.update(sessionId, "shipping_info", null);

        Optional<SessionContextStore.SessionContext> contextOpt = store.get(sessionId);
        assertTrue(contextOpt.isPresent());
        assertEquals("shipping_info", contextOpt.get().lastIntent());
        assertEquals("ORD-9911", contextOpt.get().lastOrderId(), "Existing order ID must be retained across turns");
    }

    @Test
    @DisplayName("Clearing session should remove context from store")
    void testSessionClear() {
        String sessionId = "test-session-3";
        store.update(sessionId, "return_policy", null);
        assertEquals(1, store.size());

        store.clear(sessionId);
        assertTrue(store.get(sessionId).isEmpty());
        assertEquals(0, store.size());
    }
}
