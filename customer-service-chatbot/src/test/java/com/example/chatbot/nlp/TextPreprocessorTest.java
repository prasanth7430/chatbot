package com.example.chatbot.nlp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextPreprocessorTest {

    private TextPreprocessor preprocessor;

    @BeforeEach
    void setUp() {
        preprocessor = new TextPreprocessor();
    }

    @Test
    @DisplayName("Stopwords should be removed from text")
    void testStopwordRemoval() {
        String input = "this is a package for you";
        List<String> tokens = preprocessor.preprocessToTokens(input);

        // 'this', 'is', 'a', 'for', 'you' are standard stopwords
        // Only 'packag' should remain
        assertEquals(1, tokens.size());
        assertEquals("packag", tokens.get(0));
    }

    @Test
    @DisplayName("Negations such as 'not', 'no', 'never', 'cannot', and 'n't' must be kept")
    void testNegationsPreserved() {
        String input1 = "I did not receive my delivery";
        List<String> tokens1 = preprocessor.preprocessToTokens(input1);
        assertTrue(tokens1.contains("not"), "Token 'not' must be retained");

        String input2 = "There is no order found";
        List<String> tokens2 = preprocessor.preprocessToTokens(input2);
        assertTrue(tokens2.contains("no"), "Token 'no' must be retained");

        String input3 = "I can't access my account and I don't remember my password";
        List<String> tokens3 = preprocessor.preprocessToTokens(input3);
        assertTrue(tokens3.contains("not"), "Contraction n't must expand and retain negation");
    }

    @Test
    @DisplayName("Porter stemmer should reduce word variants to common root")
    void testPorterStemming() {
        assertEquals("ship", preprocessor.preprocess("shipping"));
        assertEquals("ship", preprocessor.preprocess("shipped"));
        assertEquals("ship", preprocessor.preprocess("ships"));

        assertEquals("deliveri", preprocessor.preprocess("delivery"));
        assertEquals("deliveri", preprocessor.preprocess("deliveries"));
    }

    @Test
    @DisplayName("URLs, emojis, and punctuation must be stripped cleanly")
    void testUrlAndEmojiRemoval() {
        String input = "Check this tracking link https://example.com/track?id=9921 for status! 📦🚀";
        String cleaned = preprocessor.cleanText(input);

        assertFalse(cleaned.contains("https"));
        assertFalse(cleaned.contains("example.com"));
        assertFalse(cleaned.contains("!"));
        assertFalse(cleaned.contains("📦"));
        assertFalse(cleaned.contains("🚀"));
        assertTrue(cleaned.contains("track") || cleaned.contains("status"));
    }

    @Test
    @DisplayName("Empty or null text should produce empty tokens safely")
    void testEmptyAndNullHandling() {
        assertTrue(preprocessor.preprocessToTokens("").isEmpty());
        assertTrue(preprocessor.preprocessToTokens(null).isEmpty());
        assertTrue(preprocessor.preprocessToTokens("   ").isEmpty());
    }
}
