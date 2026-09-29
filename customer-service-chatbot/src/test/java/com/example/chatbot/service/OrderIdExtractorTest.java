package com.example.chatbot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class OrderIdExtractorTest {

    private OrderIdExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new OrderIdExtractor();
    }

    @Test
    @DisplayName("Should extract #12345 format")
    void testHashPrefixFormat() {
        Optional<String> id1 = extractor.extractOrderId("Where is my order #12345?");
        assertTrue(id1.isPresent());
        assertEquals("#12345", id1.get());

        Optional<String> id2 = extractor.extractOrderId("track #987654 please");
        assertTrue(id2.isPresent());
        assertEquals("#987654", id2.get());
    }

    @Test
    @DisplayName("Should extract ORD-1234 format")
    void testOrdPrefixFormat() {
        Optional<String> id1 = extractor.extractOrderId("Cancel my order ORD-1234 right now");
        assertTrue(id1.isPresent());
        assertEquals("ORD-1234", id1.get());

        Optional<String> id2 = extractor.extractOrderId("Need help with ord-9921");
        assertTrue(id2.isPresent());
        assertEquals("ord-9921", id2.get().toLowerCase());
    }

    @Test
    @DisplayName("Should extract 'order 12345' and 'order number 12345' formats")
    void testOrderNumberFormat() {
        Optional<String> id1 = extractor.extractOrderId("Where is order 12345 located?");
        assertTrue(id1.isPresent());
        assertEquals("#12345", id1.get());

        Optional<String> id2 = extractor.extractOrderId("Check status for order number 54321");
        assertTrue(id2.isPresent());
        assertEquals("#54321", id2.get());

        Optional<String> id3 = extractor.extractOrderId("order id 88412 status");
        assertTrue(id3.isPresent());
        assertEquals("#88412", id3.get());
    }

    @Test
    @DisplayName("Should return empty when no order ID is present")
    void testNoOrderId() {
        Optional<String> id = extractor.extractOrderId("Hello, what are your business hours?");
        assertTrue(id.isEmpty());

        Optional<String> idEmpty = extractor.extractOrderId("");
        assertTrue(idEmpty.isEmpty());

        Optional<String> idNull = extractor.extractOrderId(null);
        assertTrue(idNull.isEmpty());
    }
}
