package com.longdq.adaptengbackend.common.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class HuggingFaceService {

    @Value("${huggingface.api.key}")
    private String apiKey;

    // Bean riêng có timeout (connect 10s / read 120s). Field injection kèm @Qualifier
    // vì @RequiredArgsConstructor không truyền qualifier vào constructor được.
    @Autowired
    @Qualifier("huggingFaceRestTemplate")
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper;

    private static final String GRADIO_QUEUE_SUBMIT_URL = "https://black-forest-labs-flux-1-schnell.hf.space/gradio_api/call/infer";
    private static final String GRADIO_QUEUE_STREAM_URL = "https://black-forest-labs-flux-1-schnell.hf.space/gradio_api/call/infer/";

    public byte[] generateImage(String imagePrompt) {
        // Đề xuất (d): chỉ gắn Bearer khi có token thật; Space public vẫn chạy không token,
        // tránh 401 giả tạo khi token chết/trống.
        boolean hasToken = apiKey != null && !apiKey.isBlank();
        if (!hasToken) {
            log.info("HUGGINGFACE_API_KEY trống: gọi Space public không kèm Bearer token.");
        }

        try {
            log.info("🎨 BẮT ĐẦU: Xin vé xếp hàng vẽ ảnh FLUX.1-schnell trên Hugging Face...");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // 🛡️ BẢO VỆ: Nhét API Key của bạn vào để dùng Quota cá nhân, chống lỗi Rate-Limit
            if (hasToken) {
                headers.setBearerAuth(apiKey);
            }

            long randomSeed = new Random().nextInt(1000000);
            Map<String, Object> requestBody = new HashMap<>();
            Object[] dataArray = new Object[] { imagePrompt, randomSeed, true, 1024, 768, 4 };
            requestBody.put("data", dataArray);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            // ==========================================
            // BƯỚC 1: XIN VÉ XẾP HÀNG (LẤY EVENT_ID)
            // ==========================================
            JsonNode submitResponse;
            try {
                submitResponse = restTemplate.postForObject(GRADIO_QUEUE_SUBMIT_URL, request, JsonNode.class);
            } catch (Exception e) {
                // Đề xuất (a): bóc nguyên nhân gốc (status + body) thay vì message chung chung
                throw new RuntimeException("Xin vé Gradio thất bại (Space có thể sleep/treo). Nguyên nhân gốc: "
                        + rootCauseMessage(e), e);
            }
            if (submitResponse == null || !submitResponse.has("event_id")) {
                throw new RuntimeException("Không lấy được vé xếp hàng (event_id) từ Gradio. Response thô: "
                        + submitResponse);
            }

            String eventId = submitResponse.get("event_id").asText();
            log.info("🎫 Lấy vé thành công (Event ID: {}). Đang lắng nghe luồng dữ liệu...", eventId);

            // ==========================================
            // BƯỚC 2: LẮNG NGHE STREAM CHO ĐẾN KHI VẼ XONG
            // ==========================================
            String streamUrl = GRADIO_QUEUE_STREAM_URL + eventId;

            // Cài API Key vào lệnh GET lắng nghe Stream (chỉ khi có token)
            RequestCallback requestCallback = request1 -> {
                if (hasToken) {
                    request1.getHeaders().setBearerAuth(apiKey);
                }
            };

            // Đề xuất (a): giữ lại raw error text từ SSE để ném kèm exception
            AtomicReference<String> sseError = new AtomicReference<>(null);
            AtomicReference<String> urlRejectReason = new AtomicReference<>(null);

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
                                String url = dataNode.get(0).path("url").asText(null);
                                // Đề xuất (c): guard url trước khi trả về cho bước tải ảnh
                                if (isUsableImageUrl(url)) {
                                    return url;
                                }
                                urlRejectReason.set("Gradio trả url không dùng được (null/local-path): path="
                                        + dataNode.get(0).path("path").asText(null) + ", url=" + url);
                                return null;
                            }
                            urlRejectReason.set("Payload complete không phải mảng JSON như kỳ vọng: " + jsonData);
                            return null;
                        } else if (line.trim().equals("event: error")) {
                            String errorDetail = reader.readLine(); // Đọc nguyên nhân lỗi
                            sseError.set(errorDetail);
                            log.warn("⚠️ Space FLUX báo lỗi (có thể hết ZeroGPU quota/quá tải). Event {}: {}",
                                    eventId, errorDetail);
                            return null;
                        }
                    }
                }
                return null;
            };

            String imageUrl;
            try {
                imageUrl = restTemplate.execute(streamUrl, HttpMethod.GET, requestCallback, responseExtractor);
            } catch (Exception e) {
                throw new RuntimeException("Nghe stream Gradio thất bại (Event " + eventId
                        + ", có thể timeout 120s do Space cold-start/nghẽn mạng). Nguyên nhân gốc: "
                        + rootCauseMessage(e), e);
            }

            // ==========================================
            // BƯỚC 3: TẢI ẢNH TỪ URL TRẢ VỀ (có guard)
            // ==========================================
            if (imageUrl != null && !imageUrl.isBlank()) {
                log.info("✅ FLUX đã vẽ xong! Đang tải file ảnh về Backend từ: {}", imageUrl);
                byte[] imageBytes = restTemplate.getForObject(imageUrl, byte[].class);
                if (imageBytes != null && imageBytes.length > 0) {
                    return imageBytes;
                }
                throw new RuntimeException("Tải ảnh thất bại: URL trả về rỗng (url=" + imageUrl + ").");
            }

            if (sseError.get() != null) {
                throw new RuntimeException("Space FLUX báo lỗi, không có ảnh. Chi tiết SSE: " + sseError.get());
            }
            if (urlRejectReason.get() != null) {
                throw new RuntimeException("Không lấy được link ảnh. " + urlRejectReason.get());
            }
            throw new RuntimeException("Luồng stream kết thúc nhưng không lấy được link ảnh (Event " + eventId + ").");

        } catch (RuntimeException e) {
            // Đề xuất (a): ném tiếp kèm full nguyên nhân gốc, tuyệt đối không nuốt chi tiết
            if (e.getMessage() != null && e.getMessage().contains("Nguyên nhân gốc")) {
                throw e;
            }
            log.error("❌ Lỗi khi vẽ ảnh FLUX: {}", e.getMessage(), e);
            throw new RuntimeException("Vẽ ảnh qua Gradio Queue thất bại. Nguyên nhân gốc: "
                    + rootCauseMessage(e), e);
        }
    }

    /** URL ảnh hợp lệ: http(s) remote hoặc base64 data-uri; loại local path của server Gradio. */
    private boolean isUsableImageUrl(String url) {
        if (url == null || url.isBlank() || url.equalsIgnoreCase("null")) {
            return false;
        }
        String lower = url.toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("data:");
    }

    /** Bóc message của cause sâu nhất để log/ném kèm, tránh message bọc ngoài chung chung. */
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
}
