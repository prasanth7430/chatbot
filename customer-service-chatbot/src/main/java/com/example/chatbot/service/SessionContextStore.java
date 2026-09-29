package com.example.chatbot.service;

import com.example.chatbot.config.ChatbotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory session context store with size bounding and TTL-based eviction.
 * Maintains conversation history, last recognized intent, and active order ID per session.
 */
@Component
public class SessionContextStore {

    private static final Logger log = LoggerFactory.getLogger(SessionContextStore.class);

    private final ConcurrentHashMap<String, SessionContext> sessions = new ConcurrentHashMap<>();
    private final int maxEntries;
    private final int ttlMinutes;

    public record SessionContext(
            String sessionId,
            String lastIntent,
            String lastOrderId,
            Instant lastAccessed
    ) {
        public SessionContext withAccess(Instant now) {
            return new SessionContext(sessionId, lastIntent, lastOrderId, now);
        }

        public SessionContext withUpdate(String newIntent, String newOrderId, Instant now) {
            String effectiveOrderId = (newOrderId != null && !newOrderId.isBlank()) ? newOrderId : this.lastOrderId;
            String effectiveIntent = (newIntent != null && !newIntent.isBlank()) ? newIntent : this.lastIntent;
            return new SessionContext(sessionId, effectiveIntent, effectiveOrderId, now);
        }
    }

    public SessionContextStore(ChatbotProperties properties) {
        this.maxEntries = properties.getSession().getMaxEntries();
        this.ttlMinutes = properties.getSession().getTtlMinutes();
    }

    /**
     * Retrieves the session context for a given session ID, updating its access time.
     *
     * @param sessionId Unique session ID.
     * @return Optional containing the session context, or empty if not present or expired.
     */
    public Optional<SessionContext> get(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }

        SessionContext context = sessions.get(sessionId);
        if (context == null) {
            return Optional.empty();
        }

        // Check TTL
        Instant cutoff = Instant.now().minus(ttlMinutes, ChronoUnit.MINUTES);
        if (context.lastAccessed().isBefore(cutoff)) {
            sessions.remove(sessionId);
            log.debug("Session {} expired and was evicted", sessionId);
            return Optional.empty();
        }

        // Touch last accessed
        SessionContext updated = context.withAccess(Instant.now());
        sessions.put(sessionId, updated);
        return Optional.of(updated);
    }

    /**
     * Updates or creates session context for the given session ID.
     *
     * @param sessionId Session ID.
     * @param intent    Recognized intent for the turn.
     * @param orderId   Order ID extracted from turn, if any.
     * @return The updated SessionContext.
     */
    public SessionContext update(String sessionId, String intent, String orderId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Session ID cannot be blank");
        }

        cleanupIfNecessary();

        Instant now = Instant.now();
        return sessions.compute(sessionId, (key, existing) -> {
            if (existing == null) {
                return new SessionContext(sessionId, intent, orderId, now);
            } else {
                return existing.withUpdate(intent, orderId, now);
            }
        });
    }

    /**
     * Clears context for a specific session ID.
     *
     * @param sessionId The session ID to remove.
     */
    public void clear(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
            log.info("Cleared session context for: {}", sessionId);
        }
    }

    /**
     * Clears all session data.
     */
    public void clearAll() {
        sessions.clear();
    }

    /**
     * Returns the current number of active sessions in memory.
     */
    public int size() {
        return sessions.size();
    }

    /**
     * Removes expired entries or evicts the oldest entry if max entries threshold is exceeded.
     */
    private void cleanupIfNecessary() {
        if (sessions.size() < maxEntries) {
            return;
        }

        Instant cutoff = Instant.now().minus(ttlMinutes, ChronoUnit.MINUTES);
        sessions.entrySet().removeIf(entry -> entry.getValue().lastAccessed().isBefore(cutoff));

        // If still at capacity, evict oldest entry
        if (sessions.size() >= maxEntries) {
            String oldestKey = null;
            Instant oldestTime = Instant.MAX;
            for (Map.Entry<String, SessionContext> entry : sessions.entrySet()) {
                if (entry.getValue().lastAccessed().isBefore(oldestTime)) {
                    oldestTime = entry.getValue().lastAccessed();
                    oldestKey = entry.getKey();
                }
            }
            if (oldestKey != null) {
                sessions.remove(oldestKey);
                log.debug("Evicted oldest session {} to enforce max entries limit", oldestKey);
            }
        }
    }
}
