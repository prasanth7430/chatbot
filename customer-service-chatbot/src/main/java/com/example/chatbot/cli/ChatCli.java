package com.example.chatbot.cli;

import com.example.chatbot.model.ChatResponse;
import com.example.chatbot.service.ChatBotService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Scanner;
import java.util.UUID;

/**
 * Interactive command-line interface for the customer service chatbot.
 * Activated only when the "cli" Spring profile is specified.
 */
@Component
@Profile("cli")
public class ChatCli implements CommandLineRunner {

    private final ChatBotService chatBotService;

    public ChatCli(ChatBotService chatBotService) {
        this.chatBotService = chatBotService;
    }

    @Override
    public void run(String... args) {
        String sessionId = "cli-" + UUID.randomUUID().toString().substring(0, 8);
        Scanner scanner = new Scanner(System.in);

        System.out.println("==============================================================");
        System.out.println("        Customer Service Chatbot CLI (Session: " + sessionId + ")");
        System.out.println("        Type your message and press ENTER.");
        System.out.println("        Type 'exit' or 'quit' to terminate.");
        System.out.println("==============================================================");

        while (true) {
            System.out.print("\nYou > ");
            if (!scanner.hasNextLine()) {
                break;
            }

            String line = scanner.nextLine().trim();
            if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) {
                System.out.println("Chatbot > Thank you for using our customer support CLI. Goodbye!");
                break;
            }

            if (line.isEmpty()) {
                continue;
            }

            ChatResponse response = chatBotService.handle(sessionId, line);
            System.out.println("Chatbot [" + response.intent() + " | conf: "
                    + String.format("%.2f", response.confidence()) + "] > " + response.response());

            if (response.suggestions() != null && !response.suggestions().isEmpty()) {
                System.out.println("Suggestions: " + String.join(" | ", response.suggestions()));
            }
        }
    }
}
