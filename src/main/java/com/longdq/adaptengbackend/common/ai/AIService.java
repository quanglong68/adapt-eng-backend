package com.longdq.adaptengbackend.common.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.longdq.adaptengbackend.common.enums.KnowledgeType;
import com.longdq.adaptengbackend.common.enums.Level;
import com.longdq.adaptengbackend.common.ai.AIPromptTemplates;
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

    // =======================================================
    // 🚀 HÀM MỚI CHO DEEP DIVE WORKER
    // =======================================================
    public String generateDeepDiveRawJson(String prompt) {
        try {
            return callGeminiAPI(prompt);
        } catch (Exception e) {
            log.error("AI error during VIP Deep Dive generation: {}", e.getMessage(), e);
            return null;
        }
    }
    // =======================================================

    public String generateToeicPart5(Level level, KnowledgeType specificType, String targetWord, int quantity) {
        return callGeminiAPI(AIPromptTemplates.buildToeicPart5Prompt(level, specificType, targetWord, quantity));
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

    public String generateVipEntertainment(List<String> words) {
        try {
            return callGeminiAPI(AIPromptTemplates.buildVipEntertainmentPrompt(words));
        } catch (Exception e) {
            log.error("AI error during VIP entertainment generation: {}", e.getMessage(), e);
            return null;
        }
    }

    // =======================================================
    // 🚀 HÀM GỌI GEMINI NGHĨ KỊCH BẢN CHO WRITING PART 1
    // =======================================================
    // =======================================================
    // 🚀 HÀM GỌI GEMINI NGHĨ KỊCH BẢN CHO WRITING PART 1
    // =======================================================
    public String generateWritingPart1Questions(Level level, int quantity) {
        try {
            // SỬA Ở ĐÂY: Thêm chữ "Test" vào giữa tên hàm
            return callGeminiAPI(AIPromptTemplates.buildWritingPart1TestQuestionGenerationPrompt(level, quantity));
        } catch (Exception e) {
            log.error("Lỗi AI khi sinh kịch bản câu hỏi Writing: {}", e.getMessage(), e);
            return null;
        }
    }

    // =======================================================
    // 🎯 SINH WRITING PART 1 XOÁY ĐÚNG 1 NGỮ PHÁP (DÙNG CHO JOB QUÉT SM-2)
    // =======================================================
    public String generateWritingPart1Questions(Level level, String targetGrammar, int quantity) {
        try {
            if (targetGrammar == null || targetGrammar.isBlank()) {
                // Không có ngữ pháp mục tiêu -> quay về luồng random theo level
                return generateWritingPart1Questions(level, quantity);
            }
            return callGeminiAPI(
                    AIPromptTemplates.buildWritingPart1GrammarQuestionGenerationPrompt(level, targetGrammar, quantity));
        } catch (Exception e) {
            log.error("Lỗi AI khi sinh câu hỏi Writing theo ngữ pháp '{}': {}", targetGrammar, e.getMessage(), e);
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

    // =======================================================
    // 🚀 HÀM MỚI CHO WRITING TEST (GEMINI VISION - ĐA PHƯƠNG THỨC)
    // =======================================================
    public String gradeWritingPart1(String prompt, List<String> imageUrls) {
        try {
            return callGeminiVisionAPI(prompt, imageUrls);
        } catch (Exception e) {
            log.error("AI error during Writing Grading: {}", e.getMessage(), e);
            return null;
        }
    }

    private String callGeminiVisionAPI(String prompt, List<String> imageUrls) {
        try {
            // Danh sách chứa cả Text Prompt và các cục Image
            java.util.List<Map<String, Object>> partsList = new java.util.ArrayList<>();

            // 1. Nhét Text Prompt vào đầu tiên
            Map<String, Object> textPart = new HashMap<>();
            textPart.put("text", prompt);
            partsList.add(textPart);

            // 2. Tải từng ảnh về dưới dạng Base64 và nhét vào request
            for (String url : imageUrls) {
                try {
                    // Dùng RestTemplate tải ảnh dưới dạng byte array
                    byte[] imageBytes = restTemplate.getForObject(url, byte[].class);
                    if (imageBytes != null) {
                        String base64Image = java.util.Base64.getEncoder().encodeToString(imageBytes);

                        Map<String, Object> inlineData = new HashMap<>();
                        inlineData.put("mimeType", "image/jpeg"); // Gemini hỗ trợ jpeg/png/webp
                        inlineData.put("data", base64Image);

                        Map<String, Object> imagePart = new HashMap<>();
                        imagePart.put("inlineData", inlineData);

                        partsList.add(imagePart);
                    }
                } catch (Exception e) {
                    log.warn("Không thể tải ảnh từ URL: {}. Bỏ qua ảnh này.", url);
                }
            }

            Map<String, Object> contentMap = new HashMap<>();
            contentMap.put("parts", partsList);

            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("responseMimeType", "application/json");

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("generationConfig", generationConfig);
            requestBody.put("contents", List.of(contentMap));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            // MẸO: Gemini 1.5 Flash/Pro hỗ trợ cực tốt cho Vision
            String model = apiUrl.contains("gemini-1.5") ? apiUrl : apiUrl.replace("gemini-pro", "gemini-1.5-flash");
            String response = restTemplate.postForObject(model + "?key=" + apiKey, request, String.class);

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
            log.error("Gemini Vision API connection error: {}", e.getMessage(), e);
            return null;
        }
    }
}