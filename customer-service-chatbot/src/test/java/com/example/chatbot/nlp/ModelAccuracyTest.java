package com.example.chatbot.nlp;

import com.example.chatbot.model.EvaluationReport;
import com.example.chatbot.service.ChatBotService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ModelAccuracyTest {

    @Autowired
    private ChatBotService chatBotService;

    @Test
    @DisplayName("Held-out evaluation accuracy must be at least 90%")
    void testHeldOutAccuracyAboveNinetyPercent() {
        EvaluationReport report = chatBotService.getEvaluationReport();
        assertNotNull(report, "Evaluation report must not be null");

        double accuracy = report.accuracy();
        System.out.printf("Held-out accuracy: %.2f%% (%d/%d correct)%n",
                accuracy * 100.0, report.correctSamples(), report.totalSamples());

        assertTrue(accuracy >= 0.90, "Model accuracy must be >= 0.90 (90%), but was: " + accuracy);
        assertTrue(report.macroF1() >= 0.85, "Macro F1 must be >= 0.85");

        // Verify confusion matrix file was written
        assertTrue(Files.exists(Paths.get("target/reports/confusion-matrix.csv")),
                "target/reports/confusion-matrix.csv must be created during evaluation");
    }
}
