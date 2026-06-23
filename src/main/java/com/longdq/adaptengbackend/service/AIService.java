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

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // ==========================================================
    // CÁC HÀM TIỆN ÍCH: TẠO MENU ENUM CHUẨN XÁC CHO TỪNG PART TOEIC
    // ==========================================================
    private String getAllowedKnowledgeTypes() {
        return Arrays.stream(KnowledgeType.values())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }

    private String getPart5AllowedEnums() {
        return "VOCABULARY, COLLOCATIONS, PHRASAL_VERBS, WORD_FORMATION, " +
                "PRONOUNS_PERSONAL, PRONOUNS_POSSESSIVE, PRONOUNS_REFLEXIVE, PRONOUNS_INDEFINITE, " +
                "NOUNS_COUNTABLE_UNCOUNTABLE, QUANTIFIERS, ARTICLES, " +
                "PREPOSITIONS_TIME, PREPOSITIONS_PLACE, PREPOSITIONS_OTHER, " +
                "COORDINATING_CONJUNCTIONS, SUBORDINATING_CONJUNCTIONS, CORRELATIVE_CONJUNCTIONS, " +
                "SUBJECT_VERB_AGREEMENT, PASSIVE_VOICE, MODAL_VERBS, GERUNDS, INFINITIVES, PARTICIPLES, " +
                "CONDITIONAL_TYPE_1, CONDITIONAL_TYPE_2, CONDITIONAL_TYPE_3, CONDITIONAL_MIXED, " +
                "COMPARISON_EQUALITY, COMPARISON_COMPARATIVE, COMPARISON_SUPERLATIVE, " +
                "RELATIVE_CLAUSES, NOUN_CLAUSES, ADVERBIAL_CLAUSES, ADJECTIVES, ADVERBS, " +
                "PRESENT_SIMPLE, PRESENT_CONTINUOUS, PRESENT_PERFECT, PRESENT_PERFECT_CONTINUOUS, " +
                "PAST_SIMPLE, PAST_CONTINUOUS, PAST_PERFECT, PAST_PERFECT_CONTINUOUS, " +
                "FUTURE_SIMPLE, FUTURE_CONTINUOUS, FUTURE_PERFECT, FUTURE_PERFECT_CONTINUOUS";
    }

    private String getPart6AllowedEnums() {
        // ĐÃ BỔ SUNG NGỮ PHÁP (PASSIVE_VOICE, GERUNDS...) VÀO ĐÂY THEO CHUẨN TOEIC
        return "VOCABULARY, COLLOCATIONS, WORD_FORMATION, " +
                "PRONOUNS_PERSONAL, PRONOUNS_POSSESSIVE, PRONOUNS_REFLEXIVE, PRONOUNS_INDEFINITE, " +
                "PRESENT_SIMPLE, PRESENT_CONTINUOUS, PRESENT_PERFECT, PRESENT_PERFECT_CONTINUOUS, " +
                "PAST_SIMPLE, PAST_CONTINUOUS, PAST_PERFECT, PAST_PERFECT_CONTINUOUS, " +
                "FUTURE_SIMPLE, FUTURE_CONTINUOUS, FUTURE_PERFECT, FUTURE_PERFECT_CONTINUOUS, " +
                "PASSIVE_VOICE, GERUNDS, INFINITIVES, PARTICIPLES, ADJECTIVES, ADVERBS, " +
                "TRANSITIONS, TEXT_COHESION";
    }

    private String getPart7AllowedEnums() {
        return "SYNONYM, MAIN_IDEA, AUTHOR_PURPOSE, SPECIFIC_DETAILS, " +
                "INFERENCE, CROSS_REFERENCING, NOT_TRUE_QUESTION, SENTENCE_INSERTION";
    }

    // ==========================================
    // LÕI GIAO TIẾP VỚI GEMINI API
    // ==========================================
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

            if (rawText != null && !rawText.isEmpty()) {
                return rawText.replaceAll("^```json\\s*", "")
                        .replaceAll("^```\\s*", "")
                        .replaceAll("\\s*```$", "")
                        .trim();
            }
            return null;
        } catch (Exception e) {
            System.err.println("❌ Lỗi kết nối Gemini API: " + e.getMessage());
            return null;
        }
    }

    // ==========================================
    // 1. GENERATE PART 5
    // ==========================================
    public String generateToeicPart5(Level level, KnowledgeType specificType, String targetWord) {
        boolean isDailySpaced = (specificType != null && targetWord != null);
        String allowedEnums = getPart5AllowedEnums();

        String conditionPrompt = isDailySpaced ?
                String.format("- You MUST generate exactly ONE question that explicitly tests the specific item: '%s' with KnowledgeType: '%s'. The 'correctAnswer' must be this word.", targetWord, specificType.name()) :
                "- Distribute questions evenly between Grammar and Vocabulary items.";

        String prompt = String.format("""
            You are an expert ETS TOEIC test creator. Generate exactly 15 incomplete sentences for TOEIC Part 5 at English difficulty level: %s.
            
            Strict constraints:
            %s
            - Total questions: Exactly 15.
            - All questions must be valid multiple choice with 4 options.
            
            MANDATORY OBJECT FIELDS (EVERY question object MUST contain):
            1. "content": The question text.
            2. "options": Array of exactly 4 strings.
            3. "correctAnswer": The exact correct string.
            4. "explanation": Giải thích chi tiết bằng Tiếng Việt. Phân tích tại sao đáp án đúng lại đúng, BẮT BUỘC có phần 'Các đáp án còn lại sai vì:' và giải thích chi tiết lỗi sai của 3 đáp án kia.
            5. "knowledgeName": Tên chủ điểm NGỮ PHÁP HOẶC TỪ VỰNG cụ thể bằng tiếng Việt (VD: Thì Hiện tại Hoàn thành, Câu điều kiện loại 1, Mạo từ...).
            6. "knowledgeType": CRITICAL: MUST EXACTLY match ONE of the following values: [%s]. DO NOT invent new ones.
            7. "targetWord": STRICT IF-ELSE RULE: If 'knowledgeType' is VOCABULARY, COLLOCATIONS, PHRASAL_VERBS, or WORD_FORMATION, this MUST be the tested word (base form). If 'knowledgeType' is ANY Grammar topic (e.g., Tenses, Clauses, PASSIVE_VOICE, PREPOSITIONS), this MUST BE null. NEVER put verbs or grammar particles here.
            
            Return strictly a JSON array format:
            [
              {
                "content": "The company decided to _______ the launch until next month.",
                "options": ["postpone", "postponing", "postponed", "postpones"],
                "correctAnswer": "postpone",
                "explanation": "Cấu trúc decide to + V-inf. Các đáp án còn lại sai vì: B là V-ing, C là quá khứ phân từ...",
                "knowledgeName": "Từ vựng: Hoãn lại",
                "knowledgeType": "VOCABULARY",
                "targetWord": "postpone"
              }
            ]
            """, level.name(), conditionPrompt, allowedEnums);

        return callGeminiAPI(prompt);
    }

    // ==========================================
    // 2. GENERATE PART 6
    // ==========================================
    public String generateToeicPart6Single(Level level, KnowledgeType sm2Type, String sm2TargetWord) {
        boolean hasSpacedItem = (sm2Type != null && sm2TargetWord != null);
        String allowedEnums = getPart6AllowedEnums();

        // ĐÃ SỬA: Không ép AI bốc targetWord vô cớ nữa
        String coreMissionPrompt = hasSpacedItem ?
                String.format("""
                - index [0] (Question 1 / Blank [1]): MUST strictly test the word/grammar item: '%s' with knowledgeType: '%s'. The 'correctAnswer' must be this word/option.
                """, sm2TargetWord, sm2Type.name()) :
                """
                - index [0] (Question 1 / Blank [1]): Decide whether to test a Grammar rule OR a Vocabulary word. Assign the correct 'knowledgeType' accordingly.
                """;

        String prompt = String.format("""
            You are an expert ETS TOEIC test creator. Generate EXACTLY 1 reading passage (e.g., Memo, Email, Article) for TOEIC Part 6 at difficulty level: %s.
            The passage text MUST have exactly 4 blank spaces represented as [1], [2], [3], [4].
            
            CRITICAL NEGATIVE CONSTRAINT: DO NOT provide root words or hints in parentheses next to the blanks. 
            WRONG: "...our company will [1] (celebrate) on Saturday..."
            CORRECT: "...our company will [1] on Saturday..."
            
            The architecture of the "questions" array MUST strictly follow this index-based rule:
            %s
            - index [1], [2], and [3] (Questions 2, 3, 4): These 3 questions must test the reading context. Each question must uniquely take ONE different type from this list: [TRANSITIONS, TEXT_COHESION, PRONOUNS_PERSONAL, PRONOUNS_POSSESSIVE, PRONOUNS_REFLEXIVE, PRONOUNS_INDEFINITE]. No duplicates allowed. Their 'targetWord' fields must be null.
            
            MANDATORY OBJECT FIELDS FOR EVERY QUESTION:
            1. "content": MUST BE "Choose the best option for blank [X]" (Where X is 1, 2, 3, or 4).
            2. "options": Array of exactly 4 strings.
            3. "correctAnswer": The exact correct string.
            4. "explanation": Giải thích chi tiết bằng Tiếng Việt. Phân tích tại sao đáp án đúng lại đúng, BẮT BUỘC có phần 'Các đáp án còn lại sai vì:'.
            5. "knowledgeName": Tên kỹ năng cụ thể bằng tiếng Việt (VD: Từ vựng, Liên kết câu, Thể bị động, Thì hiện tại đơn...).
            6. "knowledgeType": CRITICAL: MUST EXACTLY match ONE of the following values: [%s]. DO NOT invent new ones.
            7. "targetWord": STRICT IF-ELSE RULE: If 'knowledgeType' is VOCABULARY, COLLOCATIONS, or WORD_FORMATION, this MUST be the tested word. If 'knowledgeType' is ANY Grammar or Context topic (e.g., PASSIVE_VOICE, Tenses, Pronouns, Transitions, TEXT_COHESION), this MUST BE null. NEVER put conjugated verbs or grammar items here.
            
            Return strictly this JSON object format:
            {
              "passageContent": "Dear Employees,\\n\\nWe are pleased to announce that... [1]. Please attend... [2].",
              "questions": [
                // Array of exactly 4 question objects
              ]
            }
            """, level.name(), coreMissionPrompt, allowedEnums);

        return callGeminiAPI(prompt);
    }

    // ==========================================
    // 3A. GENERATE PART 7 - SINGLE PASSAGE
    // ==========================================
    public String generateToeicPart7Single(Level level, String synonymTargetWord) {
        boolean hasSpacedItem = (synonymTargetWord != null);
        String allowedEnums = getPart7AllowedEnums();

        String coreMissionPrompt = hasSpacedItem ?
                String.format("""
                - index [0] (Question 1): MUST be a 'SYNONYM' question testing the contextual meaning of the specific word: '%s' appearing in the passage text. The 'targetWord' field must be '%s'.
                """, synonymTargetWord, synonymTargetWord) :
                """
                - index [0] (Question 1): You must pick one word from the text and create a 'SYNONYM' question. Provide the tested word in 'targetWord'.
                """;

        String prompt = String.format("""
            You are an expert ETS TOEIC test creator. Generate EXACTLY 1 Single Passage (e.g., an email or advertisement) for TOEIC Part 7 at difficulty level: %s.
            
            The architecture of the "questions" array MUST strictly follow this index-based rule (Total 4 questions):
            %s
            - index [1], [2], and [3] (Questions 2, 3, 4): Must test reading sub-skills. Choose uniquely from: [MAIN_IDEA, AUTHOR_PURPOSE, SPECIFIC_DETAILS, INFERENCE, NOT_TRUE_QUESTION, SENTENCE_INSERTION]. Their 'targetWord' fields must be null.
            
            MANDATORY OBJECT FIELDS FOR EVERY QUESTION:
            1. "content": The actual question string (e.g., "What is the main purpose of this email?"). MUST NOT BE NULL.
            2. "options": Array of exactly 4 strings.
            3. "correctAnswer": The exact correct string.
            4. "explanation": Giải thích chi tiết bằng Tiếng Việt. Phân tích tại sao đáp án đúng lại đúng, BẮT BUỘC có phần 'Các đáp án còn lại sai vì:'.
            5. "knowledgeName": Tên kỹ năng cụ thể bằng tiếng Việt (VD: Tìm ý chính, Tìm từ đồng nghĩa, Chi tiết cụ thể...).
            6. "knowledgeType": CRITICAL: MUST EXACTLY match ONE of the following values: [%s]. DO NOT invent new ones.
            7. "targetWord": STRICT RULE: If 'knowledgeType' is SYNONYM, this MUST be the tested synonym word. For ALL OTHER types (MAIN_IDEA, SPECIFIC_DETAILS, INFERENCE, etc.), this MUST BE null.
            
            Return strictly this JSON object format:
            {
              "passageContent": "The text content of the single passage...",
              "questions": [
                 {
                   "content": "What is the main purpose of the email?",
                   "options": ["Option A", "Option B", "Option C", "Option D"],
                   "correctAnswer": "Option A",
                   "explanation": "Tác giả nhắc đến việc... Các đáp án còn lại sai vì...",
                   "knowledgeName": "Tìm ý chính đoạn văn",
                   "knowledgeType": "MAIN_IDEA",
                   "targetWord": null
                 }
                 // generate 3 more questions similar to this structure...
              ]
            }
            """, level.name(), coreMissionPrompt, allowedEnums);

        return callGeminiAPI(prompt);
    }

    // ==========================================
    // 3B. GENERATE PART 7 - MULTIPLE PASSAGE
    // ==========================================
    public String generateToeicPart7Multiple(Level level, String synonymTargetWord) {
        boolean hasSpacedItem = (synonymTargetWord != null);
        String allowedEnums = getPart7AllowedEnums();

        String coreMissionPrompt = hasSpacedItem ?
                String.format("""
                - index [0] (Question 1): MUST be a 'SYNONYM' question testing the contextual meaning of the specific word: '%s' appearing in the passage text. The 'targetWord' field must be '%s'.
                """, synonymTargetWord, synonymTargetWord) :
                """
                - index [0] (Question 1): You must pick one word from the text and create a 'SYNONYM' question. Provide the tested word in 'targetWord'.
                """;

        String prompt = String.format("""
            You are an expert ETS TOEIC test creator. Generate EXACTLY 1 Double/Triple Passage set (related business texts) for TOEIC Part 7 at difficulty level: %s.
            Separate the texts in the 'passageContent' explicitly using markers like "--- TEXT 1 ---" and "--- TEXT 2 ---".
            
            The architecture of the "questions" array MUST strictly follow this index-based rule (Total 5 questions):
            %s
            - index [1] (Question 2): MUST be a 'CROSS_REFERENCING' question (requiring the user to synthesize facts scattered across BOTH text 1 and text 2 to reach the answer). 'targetWord' must be null.
            - index [2], [3], and [4] (Questions 3, 4, 5): Must test reading sub-skills. Choose uniquely from: [MAIN_IDEA, AUTHOR_PURPOSE, SPECIFIC_DETAILS, INFERENCE, NOT_TRUE_QUESTION]. Their 'targetWord' fields must be null.
            
            MANDATORY OBJECT FIELDS FOR EVERY QUESTION:
            1. "content": The actual question string (e.g., "What is indicated about Mr. Smith?"). MUST NOT BE NULL.
            2. "options": Array of exactly 4 strings.
            3. "correctAnswer": The exact correct string.
            4. "explanation": Giải thích chi tiết bằng Tiếng Việt. Phân tích tại sao đáp án đúng lại đúng, BẮT BUỘC có phần 'Các đáp án còn lại sai vì:'.
            5. "knowledgeName": Tên kỹ năng cụ thể bằng tiếng Việt (VD: Câu hỏi đối chiếu, Suy luận ngữ cảnh...).
            6. "knowledgeType": CRITICAL: MUST EXACTLY match ONE of the following values: [%s]. DO NOT invent new ones.
            7. "targetWord": STRICT RULE: If 'knowledgeType' is SYNONYM, this MUST be the tested synonym word. For ALL OTHER types (CROSS_REFERENCING, MAIN_IDEA, SPECIFIC_DETAILS, etc.), this MUST BE null.
            
            Return strictly this JSON object format:
            {
              "passageContent": "--- TEXT 1 ---\\nFirst text here...\\n\\n--- TEXT 2 ---\\nSecond text here...",
              "questions": [
                 {
                   "content": "According to the first email, what is true about Mr. Smith?",
                   "options": ["Option A", "Option B", "Option C", "Option D"],
                   "correctAnswer": "Option A",
                   "explanation": "Trong Text 1 có nhắc đến... Các đáp án còn lại sai vì...",
                   "knowledgeName": "Chi tiết cụ thể",
                   "knowledgeType": "SPECIFIC_DETAILS",
                   "targetWord": null
                 }
              ]
            }
            """, level.name(), coreMissionPrompt, allowedEnums);

        return callGeminiAPI(prompt);
    }

    // ==========================================
    // CÁC HÀM GENERAL ENGLISH CŨ CỦA BẠN
    // ==========================================
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
                            "6. \"knowledgeName\": Tên chủ điểm NGỮ PHÁP HOẶC KỸ NĂNG cực kỳ cụ thể bằng tiếng Việt. " +
                            "7. \"knowledgeType\": BẮT BUỘC CHỈ ĐƯỢC CHỌN 1 trong các giá trị Enum sau: [%s]. " +
                            "8. \"targetWord\": ĐẠO LUẬT THÉP: Nếu 'knowledgeType' thuộc nhóm TỪ VỰNG (VOCABULARY, COLLOCATIONS...), hãy điền từ nguyên thể vào đây. NẾU LÀ CÂU HỎI NGỮ PHÁP (Thì, Bị động...) HOẶC ĐỌC HIỂU THÌ BẮT BUỘC ĐỂ null. TUYỆT ĐỐI KHÔNG điền động từ chia thì vào đây. " +
                            "Đảm bảo các câu hỏi có độ khó chuẩn %s và rải đều qua các 'knowledgeType' đã cung cấp.",
                    targetLevel, allowedEnums, targetLevel
            );
            return callGeminiAPI(prompt);
        } catch (Exception e) {
            System.err.println("Lỗi hệ thống khi gọi Gemini (Bài Test): " + e.getMessage());
            return null;
        }
    }

    public String generateDailyQuestionsForMissingItems(String promptRequirement) {
        try {
            String allowedEnums = getAllowedKnowledgeTypes();
            String prompt = "Đóng vai chuyên gia ngôn ngữ Anh. Kho dữ liệu đang thiếu câu hỏi ôn tập. " +
                    "Hãy tạo ra các câu hỏi trắc nghiệm tiếng Anh BÁM SÁT yêu cầu sau:\n\n" +
                    promptRequirement + "\n\n" +
                    "BẮT BUỘC trả về 1 JSON Array, KHÔNG bọc markdown. Mỗi object có:\n" +
                    "1. \"knowledgeId\": Giữ nguyên ID trong yêu cầu (nếu có, không thì null).\n" +
                    "2. \"targetWord\": ĐẠO LUẬT THÉP: Nếu yêu cầu tập trung kiểm tra từ vựng, hãy điền từ đó vào đây. NẾU KIỂM TRA NGỮ PHÁP (Thì, Bị động...) HOẶC ĐỌC HIỂU, BẮT BUỘC PHẢI ĐỂ null.\n" +
                    "3. \"questionType\": Luôn là \"MULTIPLE_CHOICE\".\n" +
                    "4. \"content\": Nội dung câu hỏi.\n" +
                    "5. \"options\": Mảng 4 chuỗi (A, B, C, D).\n" +
                    "6. \"correctAnswer\": Đáp án đúng (khớp với options).\n" +
                    "7. \"explanation\": Giải thích tiếng Việt (Vì sao đúng, vì sao các đáp kia sai).\n" +
                    "8. \"knowledgeName\": Tên chủ điểm NGỮ PHÁP HOẶC KỸ NĂNG cực kỳ cụ thể bằng tiếng Việt. " +
                    "9. \"knowledgeType\": BẮT BUỘC CHỈ ĐƯỢC CHỌN MỘT TRONG CÁC GIÁ TRỊ ENUM SAU (Viết hoa chính xác): [" + allowedEnums + "].";
            return callGeminiAPI(prompt);
        } catch (Exception e) {
            System.err.println("Lỗi AI (Bù kho Daily): " + e.getMessage());
            return null;
        }
    }
}