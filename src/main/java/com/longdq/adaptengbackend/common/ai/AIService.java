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

import java.util.ArrayList;
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

    // Key dự phòng (API_KEYS, cách nhau dấu phẩy) + thứ tự model fallback (GEMINI_API_MODELS)
    @Value("${gemini.api.keys:}")
    private String extraKeysRaw;

    @Value("${gemini.api.models:gemini-2.5-flash}")
    private String modelsRaw;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // Con trỏ xoay vòng key để dàn đều tải giữa các lần gọi
    private final java.util.concurrent.atomic.AtomicInteger keyCursor = new java.util.concurrent.atomic.AtomicInteger(0);

    // Mỗi cặp model-key thử tối đa 3 lần rồi bắt buộc xoay sang cặp khác
    private static final int PAIR_MAX_RETRIES = 3;
    private static final long FALLBACK_RETRY_DELAY_MS = 2000;

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

    // =======================================================
    // WRITING PART 2 & PART 3: SINH ĐỀ GENERIC + CHẤM BÀI TEXT-ONLY
    // Đề generic chuẩn ETS, không ép ngữ pháp. Chấm bài dùng callGeminiAPI text (không Vision).
    // =======================================================
    public String generateWritingPart2Question(Level level) {
        try {
            // Có retry + xoay model/key: hết quota model này thì đổi model, đổi model không được thì đổi key
            return callGeminiResilient(
                    AIPromptTemplates.buildWritingPart2GenerationPrompt(level), "Writing Part 2");
        } catch (Exception e) {
            log.error("Lỗi AI khi sinh đề Writing Part 2: {}", e.getMessage(), e);
            return null;
        }
    }

    public String generateWritingPart3Question(Level level) {
        try {
            return callGeminiResilient(
                    AIPromptTemplates.buildWritingPart3GenerationPrompt(level), "Writing Part 3");
        } catch (Exception e) {
            log.error("Lỗi AI khi sinh đề Writing Part 3: {}", e.getMessage(), e);
            return null;
        }
    }

    public String gradeWritingPart2(String promptWithInputs) {
        try {
            return callGeminiAPI(promptWithInputs);
        } catch (Exception e) {
            log.error("Lỗi AI khi chấm Writing Part 2: {}", e.getMessage(), e);
            return null;
        }
    }

    public String gradeWritingPart3(String promptWithInputs) {
        try {
            return callGeminiAPI(promptWithInputs);
        } catch (Exception e) {
            log.error("Lỗi AI khi chấm Writing Part 3: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Chấm P2/P3 có xoay model/key (dùng cho session hỗn hợp). Ném exception khi mọi
     * combo đều chết để tầng service trả lỗi rõ ràng thay vì chấm 0 điểm trong lặng lẽ.
     */
    public String gradeWritingPart2Resilient(String promptWithInputs) throws Exception {
        String result = callGeminiResilient(promptWithInputs, "Chấm Writing Part 2");
        if (result == null || result.isBlank()) {
            throw new RuntimeException("AI không trả kết quả chấm Part 2 sau khi đã xoay model/key.");
        }
        return result;
    }

    public String gradeWritingPart3Resilient(String promptWithInputs) throws Exception {
        String result = callGeminiResilient(promptWithInputs, "Chấm Writing Part 3");
        if (result == null || result.isBlank()) {
            throw new RuntimeException("AI không trả kết quả chấm Part 3 sau khi đã xoay model/key.");
        }
        return result;
    }

    /**
     * Chấm P1 Vision có xoay model/key (dùng cho session hỗn hợp).
     */
    public String gradeWritingPart1Resilient(String prompt, List<String> imageUrls) throws Exception {
        String result = callGeminiVisionResilient(prompt, imageUrls, "Chấm Writing Part 1");
        if (result == null || result.isBlank()) {
            throw new RuntimeException("AI không trả kết quả chấm Part 1 sau khi đã xoay model/key.");
        }
        return result;
    }

    // =======================================================
    // CƠ CHẾ RETRY + XOAY MODEL/KEY (hết quota model này → đổi model,
    // đổi model không được → đổi key). Trả null chỉ khi mọi combo đều thất bại.
    // =======================================================
    /** Một lần gọi Gemini với URL + key chỉ định (text hoặc Vision). */
    @FunctionalInterface
    private interface GeminiCall {
        String call(String url, String key) throws Exception;
    }

    private String callGeminiResilient(String prompt, String taskName) {
        return executeWithFallback(taskName,
                (url, key) -> callGeminiWithUrlKey(prompt, url, key));
    }

    private String callGeminiVisionResilient(String prompt, List<String> imageUrls, String taskName) {
        return executeWithFallback(taskName,
                (url, key) -> callGeminiVisionWithUrlKey(prompt, imageUrls, url, key));
    }

    /**
     * Quét toàn bộ ma trận model × key, không trần số combo: mỗi cặp thử tối đa
     * 3 lần rồi bắt buộc xoay sang cặp khác, cứ thế đến khi thành công hoặc hết
     * ma trận mới trả null (tầng service ngoài quyết định retry tiếp hay báo 429).
     */
    private String executeWithFallback(String taskName, GeminiCall call) {
        List<String> models = splitConfig(modelsRaw);
        if (models.isEmpty()) {
            log.error("Chưa cấu hình GEMINI_API_MODELS, bỏ qua {}.", taskName);
            return null;
        }
        List<String> keys = allApiKeys();
        if (keys.isEmpty()) {
            log.error("Không có GEMINI_API_KEY/API_KEYS nào được cấu hình, bỏ qua {}.", taskName);
            return null;
        }

        // Bắt đầu xoay từ con trỏ để dàn đều tải giữa các lần gọi
        int startKey = Math.floorMod(keyCursor.get(), keys.size());
        int totalPairs = models.size() * keys.size();
        int donePairs = 0;

        for (String model : models) {
            String modelUrl = buildModelUrl(model);
            for (int i = 0; i < keys.size(); i++) {
                String key = keys.get((startKey + i) % keys.size());
                donePairs++;
                for (int attempt = 1; attempt <= PAIR_MAX_RETRIES; attempt++) {
                    try {
                        String result = call.call(modelUrl, key);
                        // Thành công: tiến con trỏ để lần sau dùng key kế tiếp
                        keyCursor.incrementAndGet();
                        if (donePairs > 1 || attempt > 1) {
                            log.info("Gọi {} thành công ở cặp {}/{} (model={}, key=...{}), lần thử {}/{}.",
                                    taskName, donePairs, totalPairs, model, maskKey(key),
                                    attempt, PAIR_MAX_RETRIES);
                        }
                        return result;
                    } catch (Exception e) {
                        // Hết quota/rate-limit/key hỏng → thử lại cặp này rồi xoay tiếp, không dừng ngay
                        log.warn("Gọi {} thất bại ở cặp {}/{} (model={}, key=...{}), lần thử {}/{}. Nguyên nhân gốc: {}",
                                taskName, donePairs, totalPairs, model, maskKey(key),
                                attempt, PAIR_MAX_RETRIES, rootCauseMessage(e));
                        sleepQuietly(FALLBACK_RETRY_DELAY_MS);
                    }
                }
                log.info("Cặp (model={}, key=...{}) đã thử {} lần không được, xoay sang cặp khác.",
                        model, maskKey(key), PAIR_MAX_RETRIES);
            }
        }
        log.error("Mọi cặp model/key đều đã thử {} lần mà vẫn thất bại cho {}.", PAIR_MAX_RETRIES, taskName);
        return null;
    }

    /** Key chính + key dự phòng, loại trống/trùng, giữ thứ tự (key chính trước). */
    private List<String> allApiKeys() {
        List<String> keys = new ArrayList<>();
        if (apiKey != null && !apiKey.isBlank()) {
            keys.add(apiKey.trim());
        }
        for (String k : splitConfig(extraKeysRaw)) {
            if (!keys.contains(k)) {
                keys.add(k);
            }
        }
        return keys;
    }

    private List<String> splitConfig(String raw) {
        List<String> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String part : raw.split(",")) {
            String v = part.trim();
            if (!v.isEmpty() && !result.contains(v)) {
                result.add(v);
            }
        }
        return result;
    }

    /** Thay model trong URL (đoạn /models/<id>:...), giữ nguyên base/suffix đã cấu hình. */
    private String buildModelUrl(String model) {
        int m = apiUrl.indexOf("/models/");
        int c = apiUrl.lastIndexOf(':');
        if (m >= 0 && c > m) {
            return apiUrl.substring(0, m + 8) + model + apiUrl.substring(c);
        }
        return apiUrl;
    }

    private String maskKey(String key) {
        if (key == null || key.length() <= 4) {
            return "****";
        }
        return key.substring(key.length() - 4);
    }

    private String rootCauseMessage(Throwable e) {
        Throwable cur = e;
        String msg = e.getMessage();
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
            if (cur.getMessage() != null && !cur.getMessage().isBlank()) {
                msg = cur.getMessage();
            }
        }
        return (msg == null || msg.isBlank()) ? e.toString() : msg;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String callGeminiAPI(String prompt) {
        try {
            return callGeminiWithUrlKey(prompt, apiUrl, apiKey);
        } catch (Exception e) {
            log.error("Gemini API connection error: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Gọi Gemini với URL + key chỉ định. NÉM exception (không nuốt) để tầng retry
     * phân biệt quota/key hỏng (xoay tiếp) với case khác. Trả null chỉ khi AI trả rỗng.
     */
    private String callGeminiWithUrlKey(String prompt, String url, String key) throws Exception {
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
        String response = restTemplate.postForObject(url + "?key=" + key, request, String.class);

        JsonNode rootNode = objectMapper.readTree(response);
        String rawText = rootNode.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();

        if (rawText == null || rawText.isEmpty()) {
            return null;
        }

        return rawText.replaceAll("^```json\\s*", "")
                .replaceAll("^```\\s*", "")
                .replaceAll("\\s*```$", "")
                .trim();
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
            // MẸO CŨ (giữ nguyên cho luồng Part 1 hiện tại): Gemini 1.5 Flash/Pro hỗ trợ cực tốt cho Vision
            String model = apiUrl.contains("gemini-1.5") ? apiUrl : apiUrl.replace("gemini-pro", "gemini-1.5-flash");
            return callGeminiVisionWithUrlKey(prompt, imageUrls, model, apiKey);
        } catch (Exception e) {
            log.error("Gemini Vision API connection error: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Gọi Vision với URL + key chỉ định. NÉM exception (không nuốt) để tầng retry
     * xoay model/key. Trả null chỉ khi AI trả rỗng.
     */
    private String callGeminiVisionWithUrlKey(
            String prompt, List<String> imageUrls, String url, String key) throws Exception {
        // Danh sách chứa cả Text Prompt và các cục Image
        java.util.List<Map<String, Object>> partsList = new java.util.ArrayList<>();

        // 1. Nhét Text Prompt vào đầu tiên
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("text", prompt);
        partsList.add(textPart);

        // 2. Tải từng ảnh về dưới dạng Base64 và nhét vào request
        for (String imageUrl : imageUrls) {
            try {
                // Dùng RestTemplate tải ảnh dưới dạng byte array
                byte[] imageBytes = restTemplate.getForObject(imageUrl, byte[].class);
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
                log.warn("Không thể tải ảnh từ URL: {}. Bỏ qua ảnh này.", imageUrl);
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

        String response = restTemplate.postForObject(url + "?key=" + key, request, String.class);

        JsonNode rootNode = objectMapper.readTree(response);
        String rawText = rootNode.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();

        if (rawText == null || rawText.isEmpty()) {
            return null;
        }

        return rawText.replaceAll("^```json\\s*", "")
                .replaceAll("^```\\s*", "")
                .replaceAll("\\s*```$", "")
                .trim();
    }
}