/**
 * NovaCare Support Assistant - Interactive Chatbot Client
 * Offline-first, Vanilla ES6 JavaScript
 */

(function () {
    "use strict";

    // Application State
    let sessionId = getOrCreateSessionId();
    let isRequestInProgress = false;
    let isDebugMode = false;

    // DOM Elements
    const messagesArea = document.getElementById("messagesArea");
    const messageInput = document.getElementById("messageInput");
    const chatForm = document.getElementById("chatForm");
    const sendBtn = document.getElementById("sendBtn");
    const typingIndicator = document.getElementById("typingIndicator");
    const debugToggle = document.getElementById("debugToggle");
    const clearChatBtn = document.getElementById("clearChatBtn");
    const sessionIdDisplay = document.getElementById("sessionIdDisplay");
    const errorBanner = document.getElementById("errorBanner");
    const errorMessage = document.getElementById("errorMessage");
    const closeErrorBanner = document.getElementById("closeErrorBanner");
    const menuToggle = document.getElementById("menuToggle");
    const sidebar = document.getElementById("sidebar");
    const connectionBadge = document.getElementById("connectionBadge");

    /**
     * Initializes the client application.
     */
    function init() {
        sessionIdDisplay.textContent = formatSessionId(sessionId);
        setupEventListeners();
        checkServerHealth();
    }

    /**
     * Generates or retrieves a unique persistent session ID using crypto.randomUUID with fallback.
     */
    function getOrCreateSessionId() {
        const STORAGE_KEY = "novacare_chatbot_session_id";
        let sid = sessionStorage.getItem(STORAGE_KEY);
        if (!sid) {
            if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
                sid = crypto.randomUUID();
            } else {
                sid = "sess-" + Date.now().toString(36) + "-" + Math.random().toString(36).substring(2, 9);
            }
            sessionStorage.setItem(STORAGE_KEY, sid);
        }
        return sid;
    }

    function formatSessionId(id) {
        if (!id) return "";
        return id.length > 12 ? id.substring(0, 10) + "..." : id;
    }

    /**
     * Sets up UI event listeners.
     */
    function setupEventListeners() {
        // Chat submission
        chatForm.addEventListener("submit", handleSubmit);

        // Auto-expanding textarea & Enter key submit (without shift)
        messageInput.addEventListener("keydown", (e) => {
            if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                chatForm.dispatchEvent(new Event("submit"));
            }
        });

        messageInput.addEventListener("input", autoResizeInput);

        // Debug toggle
        debugToggle.addEventListener("change", (e) => {
            isDebugMode = e.target.checked;
            toggleDebugDisplay(isDebugMode);
        });

        // Clear chat button
        clearChatBtn.addEventListener("click", resetConversation);

        // Close error banner
        closeErrorBanner.addEventListener("click", () => {
            errorBanner.style.display = "none";
        });

        // Mobile menu toggle
        if (menuToggle) {
            menuToggle.addEventListener("click", () => {
                sidebar.classList.toggle("open");
            });
        }

        // Close sidebar when clicking outside on mobile
        document.addEventListener("click", (e) => {
            if (window.innerWidth <= 820 && sidebar.classList.contains("open")) {
                if (!sidebar.contains(e.target) && !menuToggle.contains(e.target)) {
                    sidebar.classList.remove("open");
                }
            }
        });

        // Delegate click for sample chips and welcome chips
        document.addEventListener("click", (e) => {
            const chip = e.target.closest(".sample-chip, .welcome-chip, .suggestion-chip");
            if (chip && !isRequestInProgress) {
                const queryText = chip.getAttribute("data-query") || chip.textContent.trim();
                if (queryText) {
                    messageInput.value = queryText;
                    if (window.innerWidth <= 820) {
                        sidebar.classList.remove("open");
                    }
                    chatForm.dispatchEvent(new Event("submit"));
                }
            }
        });
    }

    /**
     * Auto-resizes textarea as the user types.
     */
    function autoResizeInput() {
        messageInput.style.height = "auto";
        messageInput.style.height = Math.min(messageInput.scrollHeight, 120) + "px";
    }

    /**
     * Verifies server availability and fetches intents.
     */
    async function checkServerHealth() {
        try {
            const res = await fetch("/api/intents");
            if (res.ok) {
                connectionBadge.textContent = "Online";
                connectionBadge.className = "badge badge-pulse";
            } else {
                throw new Error("API returned status " + res.status);
            }
        } catch (err) {
            connectionBadge.textContent = "Connecting...";
            connectionBadge.className = "badge";
        }
    }

    /**
     * Handles message form submission.
     */
    async function handleSubmit(e) {
        e.preventDefault();
        const text = messageInput.value.trim();
        if (!text || isRequestInProgress) return;

        // Dismiss welcome card on first message
        const welcomeCard = document.getElementById("welcomeCard");
        if (welcomeCard) {
            welcomeCard.style.display = "none";
        }

        // Render user message bubble
        appendMessage("user", text);
        messageInput.value = "";
        autoResizeInput();

        // Lock send controls & show typing
        setRequestInProgress(true);
        hideError();
        showTyping(true);

        try {
            const response = await fetch("/api/chat", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "Accept": "application/json"
                },
                body: JSON.stringify({
                    sessionId: sessionId,
                    message: text
                })
            });

            if (!response.ok) {
                let errPayload = null;
                try {
                    errPayload = await response.json();
                } catch (_) {}

                const msg = (errPayload && errPayload.message) 
                    ? errPayload.message 
                    : `Server returned error (${response.status}: ${response.statusText})`;
                showError(msg);
                appendMessage("bot", "I apologize, but I encountered an error while processing your request. Please try again.", {
                    intent: "error",
                    confidence: 0.0,
                    suggestions: ["Where is my order?", "Return policy", "Talk to an agent"]
                });
                return;
            }

            const data = await response.json();
            appendMessage("bot", data.response, {
                intent: data.intent,
                confidence: data.confidence,
                suggestions: data.suggestions
            });

        } catch (err) {
            console.error("Network or connection error:", err);
            showError("Unable to reach the chatbot server. Please verify your connection.");
            appendMessage("bot", "I'm having trouble connecting to our customer support service. Please check your network and try again.", {
                intent: "network_error",
                confidence: 0.0,
                suggestions: ["Retry question", "Talk to an agent"]
            });
        } finally {
            showTyping(false);
            setRequestInProgress(false);
            messageInput.focus();
        }
    }

    /**
     * Resets the conversation session both on server and in client UI.
     */
    async function resetConversation() {
        if (isRequestInProgress) return;

        try {
            await fetch("/api/reset", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ sessionId: sessionId })
            });
        } catch (err) {
            console.warn("Failed to notify server of session reset:", err);
        }

        // Clear messages
        messagesArea.innerHTML = `
            <div class="welcome-card" id="welcomeCard">
                <div class="welcome-icon">
                    <svg viewBox="0 0 24 24" width="36" height="36" fill="none" stroke="currentColor" stroke-width="2">
                        <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
                    </svg>
                </div>
                <h3>Conversation Cleared</h3>
                <p>Your session context has been reset. How may I assist you today?</p>
                <div class="welcome-chips">
                    <button class="welcome-chip" data-query="Where is my order #12345?">📦 Track Order #12345</button>
                    <button class="welcome-chip" data-query="What is your return policy?">🔄 Return Policy</button>
                    <button class="welcome-chip" data-query="What are your business hours?">🕒 Business Hours</button>
                    <button class="welcome-chip" data-query="Connect me to a live agent">👤 Talk to an Agent</button>
                </div>
            </div>
        `;

        // Generate new session ID
        sessionStorage.removeItem("novacare_chatbot_session_id");
        sessionId = getOrCreateSessionId();
        sessionIdDisplay.textContent = formatSessionId(sessionId);
        hideError();
    }

    /**
     * Appends a message bubble to the messages area.
     */
    function appendMessage(sender, text, meta = {}) {
        const row = document.createElement("div");
        row.className = `message-row ${sender}`;

        const avatar = document.createElement("div");
        avatar.className = "message-avatar";
        if (sender === "user") {
            avatar.innerHTML = `
                <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                    <circle cx="12" cy="7" r="4"/>
                </svg>
            `;
        } else {
            avatar.innerHTML = `
                <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                    <rect x="3" y="11" width="18" height="10" rx="2"/>
                    <circle cx="12" cy="5" r="2"/>
                    <path d="M12 7v4M8 16h0M16 16h0"/>
                </svg>
            `;
        }

        const content = document.createElement("div");
        content.className = "message-content";

        const bubble = document.createElement("div");
        bubble.className = "message-bubble";
        bubble.textContent = text;
        content.appendChild(bubble);

        // Debug badge for bot messages
        if (sender === "bot" && meta.intent) {
            const debugPill = document.createElement("div");
            debugPill.className = "debug-badge";
            debugPill.style.display = isDebugMode ? "inline-flex" : "none";
            const confPercent = (meta.confidence * 100).toFixed(1);
            debugPill.innerHTML = `Intent: <strong>${escapeHtml(meta.intent)}</strong> <span class="confidence-pill">${confPercent}%</span>`;
            content.appendChild(debugPill);
        }

        // Suggestions for bot messages
        if (sender === "bot" && meta.suggestions && meta.suggestions.length > 0) {
            const suggContainer = document.createElement("div");
            suggContainer.className = "suggestions-container";
            meta.suggestions.forEach(suggestion => {
                const chip = document.createElement("button");
                chip.className = "suggestion-chip";
                chip.type = "button";
                chip.setAttribute("data-query", suggestion);
                chip.textContent = suggestion;
                suggContainer.appendChild(chip);
            });
            content.appendChild(suggContainer);
        }

        // Timestamp
        const metaInfo = document.createElement("div");
        metaInfo.className = "message-meta";
        metaInfo.textContent = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        content.appendChild(metaInfo);

        row.appendChild(avatar);
        row.appendChild(content);
        messagesArea.appendChild(row);

        scrollToBottom();
    }

    /**
     * Toggles visibility of debug intent pills across messages.
     */
    function toggleDebugDisplay(show) {
        const debugPills = messagesArea.querySelectorAll(".debug-badge");
        debugPills.forEach(pill => {
            pill.style.display = show ? "inline-flex" : "none";
        });
    }

    function showTyping(show) {
        typingIndicator.style.display = show ? "flex" : "none";
        if (show) scrollToBottom();
    }

    function setRequestInProgress(inProgress) {
        isRequestInProgress = inProgress;
        sendBtn.disabled = inProgress;
        messageInput.disabled = inProgress;
    }

    function showError(msg) {
        errorMessage.textContent = msg;
        errorBanner.style.display = "flex";
    }

    function hideError() {
        errorBanner.style.display = "none";
    }

    function scrollToBottom() {
        messagesArea.scrollTop = messagesArea.scrollHeight;
    }

    function escapeHtml(str) {
        return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
    }

    // Start application on DOM ready
    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();
