package com.longdq.adaptengbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.longdq.adaptengbackend.enums.KnowledgeType;
import com.longdq.adaptengbackend.enums.Level;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AIService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    // Tiêm các Bean thông qua constructor
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Hàm hỗ trợ tự động lấy tất cả các giá trị Enum hiện có
     * để nhồi vào Prompt, giúp AI không bao giờ trả về sai định dạng.
     */
    private String getAllowedKnowledgeTypes() {
        return Arrays.stream(KnowledgeType.values())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }

    // 1. Dành cho bài đánh giá năng lực (Placement Test)
    public String generateTestQuestions(Level level) {
        try {
            String targetLevel = level.toString();
            String allowedEnums = getAllowedKnowledgeTypes();

            String prompt = String.format(
                    "Đóng vai một chuyên gia ngôn ngữ Anh và một kỹ sư dữ liệu. " +
                            "Nhiệm vụ của bạn là tạo ra 30 câu hỏi trắc nghiệm tiếng Anh (bao gồm ngữ pháp, từ vựng và đọc hiểu) chuẩn xác cho trình độ %s. " +
                            "YÊU CẦU ĐẦU RA BẮT BUỘC: Trả về duy nhất một mảng JSON (JSON Array), KHÔNG bọc mã markdown (như ```json), KHÔNG có bất kỳ văn bản giải thích ngoài mảng JSON. " +
                            "TUYỆT ĐỐI KHÔNG SỬ DỤNG KÝ TỰ XUỐNG DÒNG (ENTER) BÊN TRONG CÁC CHUỖI STRING. NẾU CẦN XUỐNG DÒNG, HÃY DÙNG '\\n'. " +
                            "Mỗi object trong mảng đại diện cho một câu hỏi phải có chính xác các trường sau: " +
                            "1. \"questionType\": Luôn luôn là \"MULTIPLE_CHOICE\". " +
                            "2. \"content\": Nội dung câu hỏi (bằng tiếng Anh). " +
                            "3. \"options\": Mảng gồm đúng 4 chuỗi đáp án (A, B, C, D). " +
                            "4. \"correctAnswer\": Đáp án đúng (PHẢI trùng khớp 100%% từng ký tự với một phần tử trong mảng options). " +
                            "5. \"explanation\": Giải thích chi tiết bằng Tiếng Việt. Phân tích tại sao đáp án đúng lại đúng, BẮT BUỘC có phần 'Các đáp án còn lại sai vì:' và giải thích chi tiết lỗi sai của 3 đáp án kia. " +
                            "6. \"knowledgeName\": Tên chủ điểm NGỮ PHÁP HOẶC KỸ NĂNG cực kỳ cụ thể bằng tiếng Việt (VD: Thì Hiện tại Hoàn thành, Câu điều kiện loại 1, Mạo từ A/An/The, Trạng từ tần suất, Tìm ý chính đoạn văn). TUYỆT ĐỐI KHÔNG sử dụng các tên chung chung như 'Các chủ điểm khác', 'Ngữ pháp', 'Từ vựng', 'Tổng hợp' hay 'Khác'. " +
                            "7. \"knowledgeType\": BẮT BUỘC CHỈ ĐƯỢC CHỌN 1 trong các giá trị Enum sau: [%s]. " +
                            "8. \"targetWord\": Nếu câu hỏi kiểm tra về từ vựng, phrasal verb, idiom hoặc collocations, hãy điền từ/cụm từ đó vào đây (ở dạng nguyên thể). NẾU LÀ CÂU HỎI NGỮ PHÁP HOẶC ĐỌC HIỂU THÌ ĐỂ null. " +
                            "Đảm bảo các câu hỏi có độ khó chuẩn %s và rải đều qua các 'knowledgeType' đã cung cấp.",
                    targetLevel, allowedEnums, targetLevel
            );

            Map<String, Object> part = new HashMap<>();
            part.put("text", prompt);

            Map<String, Object> content = new HashMap<>();
            content.put("parts", List.of(part));

            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("responseMimeType", "application/json");

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("generationConfig", generationConfig);
            requestBody.put("contents", List.of(content));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String urlWithKey = apiUrl + "?key=" + apiKey;

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            String response = restTemplate.postForObject(urlWithKey, request, String.class);

            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode candidates = rootNode.path("candidates");

            if (!candidates.isArray() || candidates.isEmpty()) {
                throw new RuntimeException("Gemini API lỗi hoặc không trả về dữ liệu candidates. Phản hồi thực tế: " + response);
            }

            String aiGeneratedJson = candidates.get(0)
                    .path("content")
                    .path("parts").get(0)
                    .path("text").asText();

            return aiGeneratedJson
                    .replace("```json", "")
                    .replace("```", "")
                    .trim();

        } catch (Exception e) {
            System.err.println("Lỗi hệ thống khi gọi Gemini (Bài Test): " + e.getMessage());
            return null;
        }
    }

    // 2. Dành cho Job ban đêm bổ sung câu hỏi (Daily Review)
    public String generateDailyQuestionsForMissingItems(String promptRequirement) {
        try {
            String allowedEnums = getAllowedKnowledgeTypes();

            String prompt = "Đóng vai chuyên gia ngôn ngữ Anh. Kho dữ liệu đang thiếu câu hỏi ôn tập. " +
                    "Hãy tạo ra các câu hỏi trắc nghiệm tiếng Anh BÁM SÁT yêu cầu sau:\n\n" +
                    promptRequirement + "\n\n" +
                    "BẮT BUỘC trả về 1 JSON Array, KHÔNG bọc markdown. Mỗi object có:\n" +
                    "1. \"knowledgeId\": Giữ nguyên ID trong yêu cầu (nếu có, không thì null).\n" +
                    "2. \"targetWord\": Giữ nguyên chữ trong yêu cầu (nếu có, không thì null).\n" +
                    "3. \"questionType\": Luôn là \"MULTIPLE_CHOICE\".\n" +
                    "4. \"content\": Nội dung câu hỏi.\n" +
                    "5. \"options\": Mảng 4 chuỗi (A, B, C, D).\n" +
                    "6. \"correctAnswer\": Đáp án đúng (khớp với options).\n" +
                    "7. \"explanation\": Giải thích tiếng Việt (Vì sao đúng, vì sao các đáp kia sai).\n" +
                    "8. \"knowledgeName\": Tên chủ điểm NGỮ PHÁP HOẶC KỸ NĂNG cực kỳ cụ thể bằng tiếng Việt (VD: Thì Hiện tại Hoàn thành, Câu điều kiện loại 1, Mạo từ A/An/The, Trạng từ tần suất, Tìm ý chính đoạn văn). TUYỆT ĐỐI KHÔNG sử dụng các tên chung chung như 'Các chủ điểm khác', 'Ngữ pháp', 'Từ vựng', 'Tổng hợp' hay 'Khác'. " +
                    "9. \"knowledgeType\": BẮT BUỘC CHỈ ĐƯỢC CHỌN MỘT TRONG CÁC GIÁ TRỊ ENUM SAU (Viết hoa chính xác): [" + allowedEnums + "].";

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

            String urlWithKey = apiUrl + "?key=" + apiKey;
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            String response = restTemplate.postForObject(urlWithKey, request, String.class);

            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode candidates = rootNode.path("candidates");

            if (!candidates.isArray() || candidates.isEmpty()) return null;

            String aiGeneratedJson = candidates.get(0).path("content").path("parts").get(0).path("text").asText();
            return aiGeneratedJson.replace("```json", "").replace("```", "").trim();
        } catch (Exception e) {
            System.err.println("Lỗi AI (Bù kho Daily): " + e.getMessage());
            return null;
        }
    }
}