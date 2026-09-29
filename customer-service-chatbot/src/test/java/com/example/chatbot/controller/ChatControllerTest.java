package com.example.chatbot.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /api/chat returns 200 and valid JSON for proper input")
    void testChatValidInputReturns200() throws Exception {
        Map<String, String> request = Map.of(
                "sessionId", "session-api-1",
                "message", "Hello, I need assistance with my order #12345"
        );

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent", notNullValue()))
                .andExpect(jsonPath("$.confidence", greaterThan(0.0)))
                .andExpect(jsonPath("$.response", notNullValue()))
                .andExpect(jsonPath("$.suggestions", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/chat returns 400 for blank message")
    void testChatBlankMessageReturns400() throws Exception {
        Map<String, String> request = Map.of(
                "sessionId", "session-api-2",
                "message", "   "
        );

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", containsString("message is required")));
    }

    @Test
    @DisplayName("POST /api/chat returns 400 for blank sessionId")
    void testChatBlankSessionIdReturns400() throws Exception {
        Map<String, String> request = Map.of(
                "sessionId", "",
                "message", "Hello bot"
        );

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", containsString("sessionId is required")));
    }

    @Test
    @DisplayName("GET /api/intents returns 200 with list of intent tags")
    void testGetIntentsReturns200() throws Exception {
        mockMvc.perform(get("/api/intents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(15))))
                .andExpect(jsonPath("$", hasItem("order_status")))
                .andExpect(jsonPath("$", hasItem("cancel_order")))
                .andExpect(jsonPath("$", hasItem("greeting")));
    }

    @Test
    @DisplayName("POST /api/reset returns 200 and confirms session clearing")
    void testResetSessionReturns200() throws Exception {
        Map<String, String> request = Map.of("sessionId", "session-reset-1");

        mockMvc.perform(post("/api/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.message", containsString("session-reset-1")));
    }
}
