package com.longdq.adaptengbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.longdq.adaptengbackend.enums.KnowledgeType;
import com.longdq.adaptengbackend.enums.Level;
import com.longdq.adaptengbackend.service.ai.AIPromptTemplates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public String generateToeicPart5(Level level, KnowledgeType specificType, String targetWord) {
        return callGeminiAPI(AIPromptTemplates.buildToeicPart5Prompt(level, specificType, targetWord));
    }

    public String generateToeicPart6Single(Level level, KnowledgeType sm2Type, String sm2TargetWord) {
        return callGeminiAPI(AIPromptTemplates.buildToeicPart6Prompt(level, sm2Type, sm2TargetWord));
    }

    public String generateToeicPart7Single(Level level, String synonymTargetWord) {
        return callGeminiAPI(AIPromptTemplates.buildToeicPart7SinglePrompt(level, synonymTargetWord));
    }

    public String generateToeicPart7Multiple(Level level, String synonymTargetWord) {
        return callGeminiAPI(AIPromptTemplates.buildToeicPart7MultiplePrompt(level, synonymTargetWord));
    }

    public String generateTestQuestions(Level level) {
        try {
            return callGeminiAPI(AIPromptTemplates.buildGeneralTestPrompt(level));
        } catch (Exception e) {
            log.error("System error calling Gemini for placement test: {}", e.getMessage(), e);
            return null;
        }
    }

    public String generateDailyQuestionsForMissingItems(String promptRequirement) {
        try {
            return callGeminiAPI(AIPromptTemplates.buildDailyRefillPrompt(promptRequirement));
        } catch (Exception e) {
            log.error("AI error during daily inventory refill: {}", e.getMessage(), e);
            return null;
        }
    }

    private String callGeminiAPI(String prompt) {
        try {
            Map<String, Object> part = new HashMap<>();
            part.put("text", prompt);

            Map<String, Object> contentMap = new HashMap<>();
            contentMap.put("parts", List.of(part));

            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("responseMimeType", "application/json");

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("generationConfig", generationConfig);
            requestBody.put("contents", List.of(contentMap));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            String response = restTemplate.postForObject(apiUrl + "?key=" + apiKey, request, String.class);

            JsonNode rootNode = objectMapper.readTree(response);
            String rawText = rootNode.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();

            if (rawText == null || rawText.isEmpty()) {
                return null;
            }

            return rawText.replaceAll("^```json\\s*", "")
                    .replaceAll("^```\\s*", "")
                    .replaceAll("\\s*```$", "")
                    .trim();
        } catch (Exception e) {
            log.error("Gemini API connection error: {}", e.getMessage(), e);
            return null;
        }
    }
}
