package com.longdq.adaptengbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class HuggingFaceService {

    @Value("${huggingface.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final String GRADIO_QUEUE_SUBMIT_URL = "https://black-forest-labs-flux-1-schnell.hf.space/gradio_api/call/infer";
    private static final String GRADIO_QUEUE_STREAM_URL = "https://black-forest-labs-flux-1-schnell.hf.space/gradio_api/call/infer/";

    public byte[] generateImage(String imagePrompt) {
        try {
            log.info("🎨 BẮT ĐẦU: Xin vé xếp hàng vẽ ảnh FLUX.1-schnell trên Hugging Face...");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // 🛡️ BẢO VỆ: Nhét API Key của bạn vào để dùng Quota cá nhân, chống lỗi Rate-Limit
            headers.setBearerAuth(apiKey);

            long randomSeed = new Random().nextInt(1000000);
            Map<String, Object> requestBody = new HashMap<>();
            Object[] dataArray = new Object[] { imagePrompt, randomSeed, true, 1024, 768, 4 };
            requestBody.put("data", dataArray);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            // ==========================================
            // BƯỚC 1: XIN VÉ XẾP HÀNG (LẤY EVENT_ID)
            // ==========================================
            JsonNode submitResponse = restTemplate.postForObject(GRADIO_QUEUE_SUBMIT_URL, request, JsonNode.class);
            if (submitResponse == null || !submitResponse.has("event_id")) {
                throw new RuntimeException("Không lấy được vé xếp hàng (event_id) từ Gradio.");
            }

            String eventId = submitResponse.get("event_id").asText();
            log.info("🎫 Lấy vé thành công (Event ID: {}). Đang lắng nghe luồng dữ liệu...", eventId);

            // ==========================================
            // BƯỚC 2: LẮNG NGHE STREAM CHO ĐẾN KHI VẼ XONG
            // ==========================================
            String streamUrl = GRADIO_QUEUE_STREAM_URL + eventId;

            // Cài API Key vào lệnh GET lắng nghe Stream
            RequestCallback requestCallback = request1 -> request1.getHeaders().setBearerAuth(apiKey);

            ResponseExtractor<String> responseExtractor = response -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                    String line;
                    boolean isCompleteEvent = false;

                    while ((line = reader.readLine()) != null) {
                        if (line.trim().equals("event: complete")) {
                            isCompleteEvent = true;
                        } else if (isCompleteEvent && line.startsWith("data: ")) {
                            String jsonData = line.substring(6);
                            JsonNode dataNode = objectMapper.readTree(jsonData);
                            if (dataNode.isArray() && dataNode.size() > 0) {
                                return dataNode.get(0).path("url").asText();
                            }
                        } else if (line.trim().equals("event: error")) {
                            String errorDetail = reader.readLine(); // Đọc nguyên nhân lỗi
                            log.error("❌ Máy chủ báo lỗi: {}", errorDetail);
                            return null;
                        }
                    }
                }
                return null;
            };

            String imageUrl = restTemplate.execute(streamUrl, HttpMethod.GET, requestCallback, responseExtractor);

            // ==========================================
            // BƯỚC 3: TẢI ẢNH TỪ URL TRẢ VỀ
            // ==========================================
            if (imageUrl != null && !imageUrl.isEmpty()) {
                log.info("✅ FLUX đã vẽ xong! Đang tải file ảnh về Backend từ: {}", imageUrl);
                byte[] imageBytes = restTemplate.getForObject(imageUrl, byte[].class);
                if (imageBytes != null && imageBytes.length > 0) {
                    return imageBytes;
                }
            }

            throw new RuntimeException("Luồng stream kết thúc nhưng không lấy được link ảnh.");

        } catch (Exception e) {
            log.error("❌ Lỗi toàn tập khi vẽ ảnh FLUX: {}", e.getMessage(), e);
            throw new RuntimeException("Vẽ ảnh qua Gradio Queue thất bại", e);
        }
    }
}