package com.example.chatbot;

import com.example.chatbot.config.ChatbotProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Main application entry point for the Customer Service Chatbot.
 */
@SpringBootApplication
@EnableConfigurationProperties(ChatbotProperties.class)
public class ChatbotApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatbotApplication.class, args);
    }
}
