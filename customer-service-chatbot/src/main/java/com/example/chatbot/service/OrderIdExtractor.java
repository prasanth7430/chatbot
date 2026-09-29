package com.example.chatbot.service;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts order tracking and identification tokens from user messages.
 * Supports patterns such as:
 * <ul>
 *     <li>#12345</li>
 *     <li>ORD-1234</li>
 *     <li>order 12345</li>
 *     <li>order number 12345</li>
 *     <li>order #12345</li>
 * </ul>
 */
@Component
public class OrderIdExtractor {

    // Regex pattern matching:
    // 1) ORD-\w+ (e.g. ORD-1234)
    // 2) #\w+ (e.g. #12345)
    // 3) order (number|no|id)? #?(\w+) (e.g. order 12345, order number 54321)
    private static final Pattern ORDER_ID_PATTERN = Pattern.compile(
            "(?i)(?:\\bORD-\\d+\\b)|(?:#\\d+\\b)|(?:\\border\\s+(?:number|no\\.?|id)?\\s*#?\\s*(\\d+|ORD-\\d+)\\b)"
    );

    /**
     * Extracts an order ID from the provided text, if present.
     *
     * @param text The input message text.
     * @return Optional containing the normalized order ID string, or empty if none found.
     */
    public Optional<String> extractOrderId(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        Matcher matcher = ORDER_ID_PATTERN.matcher(text);
        if (matcher.find()) {
            // If group 1 (from "order [number] X") matched, check it
            String group1 = matcher.group(1);
            if (group1 != null && !group1.isBlank()) {
                String val = group1.trim();
                if (!val.startsWith("#") && !val.toUpperCase().startsWith("ORD-")) {
                    return Optional.of("#" + val);
                }
                return Optional.of(val);
            }

            // Otherwise, whole match (e.g. #12345 or ORD-1234)
            String match = matcher.group(0).trim();
            // In case matcher found "order 12345" without capturing group
            if (match.toLowerCase().startsWith("order")) {
                String cleaned = match.replaceAll("(?i)^order\\s+(?:number|no\\.?|id)?\\s*#?", "").trim();
                return Optional.of("#" + cleaned);
            }
            return Optional.of(match);
        }

        return Optional.empty();
    }
}
