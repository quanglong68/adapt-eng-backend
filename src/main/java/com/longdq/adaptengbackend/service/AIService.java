package com.longdq.adaptengbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.longdq.adaptengbackend.enums.Level;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;


import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AIService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    // Tiêm các Bean thông qua constructor (lombok tự lo)
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public String generateTestQuestions(Level level) {
        try {
            String targetLevel = level.toString();

            String prompt = String.format(
                    "Đóng vai một chuyên gia ngôn ngữ Anh và một kỹ sư dữ liệu. " +
                            "Nhiệm vụ của bạn là tạo ra 30 câu hỏi trắc nghiệm tiếng Anh (bao gồm ngữ pháp, từ vựng và đọc hiểu) chuẩn xác cho trình độ %s. " +
                            "YÊU CẦU ĐẦU RA BẮT BUỘC: Trả về duy nhất một mảng JSON (JSON Array), KHÔNG bọc mã markdown (như ```json), KHÔNG có bất kỳ văn bản giải thích nào ngoài mảng JSON. " +
                            "TUYỆT ĐỐI KHÔNG SỬ DỤNG KÝ TỰ XUỐNG DÒNG (ENTER) BÊN TRONG NỘI DUNG CỦA CÁC CHUỖI STRING. NẾU CẦN XUỐNG DÒNG, HÃY DÙNG '\\n'. " +
                            "Mỗi object trong mảng đại diện cho một câu hỏi và chủ điểm kiến thức đi kèm, phải có chính xác các trường sau: " +
                            "1. \"questionType\": Luôn luôn là \"MULTIPLE_CHOICE\". " +
                            "2. \"content\": Nội dung câu hỏi (bằng tiếng Anh). " +
                            "3. \"options\": Mảng gồm đúng 4 chuỗi đáp án (A, B, C, D). " +
                            "4. \"correctAnswer\": Đáp án đúng (PHẢI trùng khớp 100%% từng ký tự với một phần tử trong mảng options). " +
                            "5. \"explanation\": TUYỆT ĐỐI BẮT BUỘC phải viết bằng Tiếng Việt theo ĐÚNG FORMAT sau: Đầu tiên, phân tích ngữ pháp/ngữ nghĩa và giải thích tại sao đáp án đúng lại đúng. Tiếp theo, BẮT BUỘC phải có phần 'Các đáp án còn lại sai vì:' và chỉ đích danh từng lỗi sai của 3 đáp án kia (Ví dụ: Từ X sai vì..., Từ Y sai vì...). KHÔNG ĐƯỢC LƯỜI BIẾNG LƯỢC BỎ PHẦN NÀY. " +
                            "6. \"knowledgeName\": Tên chủ điểm ngắn gọn bằng tiếng Việt (VD: Thì Hiện tại Hoàn thành, Cụm động từ, Câu điều kiện). " +
                            "7. \"knowledgeType\": Phân loại chủ điểm, BẮT BUỘC CHỈ ĐƯỢC CHỌN 1 trong các giá trị Enum sau: [TENSES, CONDITIONAL_SENTENCES, RELATIVE_CLAUSES, PREPOSITIONS, MODAL_VERBS, PASSIVE_VOICE, REPORTED_SPEECH, COMPARISONS, ARTICLES, CONJUNCTIONS, GERUNDS_AND_INFINITIVES, SUBJECT_VERB_AGREEMENT, WORD_ORDER, QUESTION_TAGS, VOCABULARY, PHRASAL_VERBS, IDIOMS, COLLOCATIONS, WORD_FORMATION, READING_COMPREHENSION, OTHERS]. " +
                            "Đảm bảo các câu hỏi có độ khó chuẩn %s và rải đều qua các 'knowledgeType' đã cung cấp.",
                    targetLevel, targetLevel
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

            // Bắt lỗi an toàn (Defensive Programming)
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode candidates = rootNode.path("candidates");

            if (!candidates.isArray() || candidates.isEmpty()) {
                throw new RuntimeException("Gemini API lỗi hoặc không trả về dữ liệu candidates. Phản hồi thực tế: " + response);
            }

            String aiGeneratedJson = candidates.get(0)
                    .path("content")
                    .path("parts").get(0)
                    .path("text").asText();

            // Fix lỗi cú pháp xuống dòng của chuỗi replace
            return aiGeneratedJson
                    .replace("```json", "")
                    .replace("```", "")
                    .trim();

        } catch (Exception e) {
            System.err.println("Lỗi hệ thống khi gọi Gemini: " + e.getMessage());
            return null; // Ở thực tế có thể quăng CustomException để handle ở GlobalExceptionHandler
        }
    }
}