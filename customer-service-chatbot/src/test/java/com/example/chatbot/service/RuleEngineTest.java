package com.example.chatbot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RuleEngineTest {

    private RuleEngine ruleEngine;

    @BeforeEach
    void setUp() {
        ruleEngine = new RuleEngine();
    }

    @Test
    @DisplayName("Human-agent rule triggers on explicit escalation queries")
    void testHumanAgentRuleTriggers() {
        Optional<String> res1 = ruleEngine.evaluateRule("I want to talk to a human");
        assertTrue(res1.isPresent());
        assertEquals("contact_human_agent", res1.get());

        Optional<String> res2 = ruleEngine.evaluateRule("connect me with an agent please");
        assertTrue(res2.isPresent());
        assertEquals("contact_human_agent", res2.get());

        Optional<String> res3 = ruleEngine.evaluateRule("can I speak with a representative?");
        assertTrue(res3.isPresent());
        assertEquals("contact_human_agent", res3.get());

        Optional<String> res4 = ruleEngine.evaluateRule("let me talk to a real person");
        assertTrue(res4.isPresent());
        assertEquals("contact_human_agent", res4.get());

        Optional<String> res5 = ruleEngine.evaluateRule("need live support right now");
        assertTrue(res5.isPresent());
        assertEquals("contact_human_agent", res5.get());
    }

    @Test
    @DisplayName("Non-escalation queries should not trigger human agent rule")
    void testNonRuleQueryDoesNotTrigger() {
        assertTrue(ruleEngine.evaluateRule("What are your business hours?").isEmpty());
        assertTrue(ruleEngine.evaluateRule("Where is my package #12345?").isEmpty());
        assertTrue(ruleEngine.evaluateRule("How do I return a shirt?").isEmpty());
        assertTrue(ruleEngine.evaluateRule("").isEmpty());
        assertTrue(ruleEngine.evaluateRule(null).isEmpty());
    }
}
