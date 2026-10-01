package com.longdq.adaptengbackend.service.ai;

import com.longdq.adaptengbackend.enums.KnowledgeType;
import com.longdq.adaptengbackend.enums.Level;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class AIPromptTemplates {

    private AIPromptTemplates() {
    }

    public static String getAllowedKnowledgeTypes() {
        return Arrays.stream(KnowledgeType.values())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }

    public static String getPart5AllowedEnums() {
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

    public static String getPart6AllowedEnums() {
        return "VOCABULARY, COLLOCATIONS, WORD_FORMATION, " +
                "PRONOUNS_PERSONAL, PRONOUNS_POSSESSIVE, PRONOUNS_REFLEXIVE, PRONOUNS_INDEFINITE, " +
                "PRESENT_SIMPLE, PRESENT_CONTINUOUS, PRESENT_PERFECT, PRESENT_PERFECT_CONTINUOUS, " +
                "PAST_SIMPLE, PAST_CONTINUOUS, PAST_PERFECT, PAST_PERFECT_CONTINUOUS, " +
                "FUTURE_SIMPLE, FUTURE_CONTINUOUS, FUTURE_PERFECT, FUTURE_PERFECT_CONTINUOUS, " +
                "PASSIVE_VOICE, GERUNDS, INFINITIVES, PARTICIPLES, ADJECTIVES, ADVERBS, " +
                "TRANSITIONS, TEXT_COHESION";
    }

    public static String getPart7AllowedEnums() {
        return "SYNONYM, MAIN_IDEA, AUTHOR_PURPOSE, SPECIFIC_DETAILS, " +
                "INFERENCE, CROSS_REFERENCING, NOT_TRUE_QUESTION, SENTENCE_INSERTION";
    }

    // =================================================================================================
    // 🚀 BỘ 3 PROMPT VIP DEEP DIVE (ÔN TẬP CHUYÊN SÂU - CHỐNG HỌC VẸT)
    // =================================================================================================

    public static String buildDeepDiveVocabularyPrompt(Level level, KnowledgeType specificType, String targetWord) {
        return String.format("""
            Đóng vai chuyên gia ra đề thi TOEIC và chuyên gia ngôn ngữ học. Nhiệm vụ: Tạo 1 Bộ đề ÔN TẬP CHUYÊN SÂU (Vocabulary Deep Dive) gồm ĐÚNG 10 câu hỏi trắc nghiệm tiếng Anh cho TỪ VỰNG: '%s'. Trọng tâm (KnowledgeType): '%s'. Độ khó: %s.

            ĐẠO LUẬT THÉP VỀ SỰ ĐA DẠNG (CHỐNG HỌC VẸT):
            Tuyệt đối KHÔNG tạo 10 câu nhàm chán chỉ bắt điền từ '%s'. Bạn BẮT BUỘC phải phân bổ 10 câu theo đúng cấu trúc siêu đa dạng sau:
            1. [2 câu] Trực diện (Definition/Usage): Đục lỗ chỗ trống là từ '%s' (hoặc các dạng chia thì/số nhiều của nó).
            2. [2 câu] Từ đồng nghĩa/Trái nghĩa ngữ cảnh: Từ '%s' ĐÃ CÓ SẴN trong câu hỏi. Yêu cầu chọn từ ở đáp án có thể thay thế hoàn hảo cho nó.
            3. [2 câu] Dạng từ (Word Family): Đục lỗ, 4 đáp án là 4 dạng từ (Noun, Verb, Adj, Adv) của '%s'. Yêu cầu điền đúng ngữ pháp.
            4. [2 câu] Cụm từ/Giới từ đi kèm (Collocations/Prepositions): Từ '%s' ĐÃ CÓ SẴN. Chỗ trống là giới từ hoặc động từ/danh từ ghép thường đi kèm với nó trong môi trường công sở.
            5. [2 câu] Tìm lỗi sai (Error Identification): Cho 4 câu hoàn chỉnh ở 4 đáp án (A, B, C, D). Câu hỏi là: "Câu nào dưới đây sử dụng từ '%s' (hoặc Word family của nó) SAI ngữ pháp hoặc ngữ cảnh?". Đáp án đúng là câu bị viết SAI.

            ĐẦU RA BẮT BUỘC:
            Trả về DUY NHẤT một mảng JSON (JSON Array). KHÔNG bọc mã markdown.
            Mỗi object chứa:
            - "questionType": Luôn là "MULTIPLE_CHOICE".
            - "content": Nội dung câu hỏi.
            - "options": Mảng đúng 4 chuỗi.
            - "correctAnswer": Đáp án đúng (khớp 100%% với options).
            - "explanation": ĐẠO LUẬT THÉP - BẮT BUỘC trình bày chính xác theo format sau (Phải dùng kí tự '\\n' để xuống dòng, KHÔNG ấn enter trực tiếp):
            [Dạng câu hỏi]\\n✅ Đáp án đúng: [Giải thích cực kỳ chi tiết tại sao đúng].\\n❌ Các đáp án sai:\\n- [Đáp án 1]: [Lý do sai].\\n- [Đáp án 2]: [Lý do sai].\\n- [Đáp án 3]: [Lý do sai].
            - "knowledgeName": Tên chủ điểm (Ví dụ: "Từ vựng: %s").
            - "knowledgeType": BẮT BUỘC LÀ "%s".
            - "targetWord": BẮT BUỘC LÀ "%s".
            """, targetWord, specificType.name(), level.name(), targetWord, targetWord, targetWord, targetWord, targetWord, targetWord, specificType.name(), specificType.name(), targetWord);
    }

    public static String buildDeepDiveGrammarPrompt(Level level, KnowledgeType specificType) {
        return String.format("""
            Đóng vai chuyên gia ra đề thi TOEIC. Nhiệm vụ: Tạo 1 Bộ đề ÔN TẬP CHUYÊN SÂU (Grammar Deep Dive) gồm ĐÚNG 10 câu hỏi trắc nghiệm tiếng Anh cho CHỦ ĐIỂM NGỮ PHÁP: '%s'. Độ khó: %s.

            ĐẠO LUẬT THÉP VỀ SỰ ĐA DẠNG (CHỐNG HỌC VẸT):
            Tuyệt đối KHÔNG tạo 10 câu đục lỗ đơn giản chỉ nhìn dấu hiệu nhận biết là làm được. BẮT BUỘC phân bổ như sau:
            1. [3 câu] Điền từ cơ bản: Nhận diện và áp dụng đúng cấu trúc '%s' trong câu đơn.
            2. [3 câu] Viết lại câu đồng nghĩa (Sentence Transformation): Câu hỏi cho sẵn 1 câu hoàn chỉnh. 4 đáp án là 4 cách viết lại câu đó. Yêu cầu chọn đáp án viết lại ĐÚNG ngữ pháp '%s' và giữ nguyên nghĩa.
            3. [2 câu] Tìm lỗi sai: Cho 4 câu hoàn chỉnh ở 4 đáp án (A, B, C, D). Yêu cầu tìm ra câu viết SAI cấu trúc '%s'.
            4. [2 câu] Ngữ pháp Ngữ cảnh (Contextual Grammar): Đưa ra một đoạn hội thoại hoặc văn bản ngắn (2-3 câu). Bắt người đọc phải hiểu ngữ nghĩa của toàn đoạn mới chia đúng được ngữ pháp '%s' (Bẫy: Cố tình loại bỏ các trạng từ chỉ thời gian rõ ràng).

            ĐẦU RA BẮT BUỘC:
            Trả về DUY NHẤT một mảng JSON (JSON Array). KHÔNG bọc mã markdown.
            Mỗi object chứa:
            - "questionType": Luôn là "MULTIPLE_CHOICE".
            - "content": Nội dung câu hỏi. (Dùng \\n nếu cần xuống dòng).
            - "options": Mảng đúng 4 chuỗi.
            - "correctAnswer": Đáp án đúng (khớp 100%% với options).
            - "explanation": ĐẠO LUẬT THÉP - BẮT BUỘC trình bày chính xác theo format sau (Dùng kí tự '\\n' để xuống dòng):
            [Dạng câu hỏi]\\n✅ Đáp án đúng: [Giải thích tại sao đúng].\\n❌ Các đáp án sai:\\n- [Đáp án 1]: [Lý do sai].\\n- [Đáp án 2]: [Lý do sai].\\n- [Đáp án 3]: [Lý do sai].
            - "knowledgeName": Tên chủ điểm ngữ pháp bằng tiếng Việt.
            - "knowledgeType": BẮT BUỘC LÀ "%s".
            - "targetWord": ĐẠO LUẬT THÉP LÀ PHẢI ĐỂ null.
            """, specificType.name(), level.name(), specificType.name(), specificType.name(), specificType.name(), specificType.name(), specificType.name());
    }

    public static String buildDeepDiveReadingPrompt(Level level, KnowledgeType specificType) {
        String allowedReadingEnums = getPart7AllowedEnums();

        return String.format("""
            Đóng vai chuyên gia ra đề thi TOEIC Part 7. Nhiệm vụ: User đang cực kỳ YẾU kỹ năng đọc hiểu: '%s'. Bạn phải tạo 1 Bộ đề ÔN TẬP CHUYÊN SÂU (Reading Deep Dive) để rèn luyện kỹ năng này. Độ khó: %s.

            YÊU CẦU CẤU TRÚC:
            Tạo ra ĐÚNG 2 ĐOẠN VĂN BẢN (Passages) mang văn phong công sở/thương mại (Email, Memo, Article...).
            - Passage 1: Đi kèm đúng 5 câu hỏi.
            - Passage 2: Đi kèm đúng 5 câu hỏi.
            Tổng cộng đúng 10 câu hỏi.

            ĐẠO LUẬT THÉP VỀ KỸ NĂNG:
            1. SIÊU TẬP TRUNG: Trong tổng 10 câu hỏi, BẮT BUỘC phải có ÍT NHẤT 6 CÂU test TRỰC TIẾP kỹ năng '%s'. 
               (Ví dụ: Nếu là AUTHOR_PURPOSE, phải liên tục hỏi 'Mục đích của email là gì?', 'Tại sao ông A viết thư này?').
            2. 4 câu còn lại có thể test các kỹ năng đọc hiểu khác trong danh sách sau: [%s].
            3. TUYỆT ĐỐI KHÔNG hỏi ngữ pháp. Chỉ kiểm tra kỹ năng Đọc hiểu (Reading Comprehension).

            ĐẦU RA BẮT BUỘC (JSON Object chứa mảng passages):
            Trả về DUY NHẤT một JSON Object. KHÔNG bọc mã markdown.
            {
              "passages": [
                {
                  "passageContent": "Nội dung bài đọc 1... (Dùng \\n để xuống dòng)",
                  "questions": [
                    {
                      "content": "Câu hỏi số 1...",
                      "options": ["A", "B", "C", "D"],
                      "correctAnswer": "A",
                      "explanation": "[Kỹ năng Đọc hiểu]\\n✅ Đáp án đúng: [Trích dẫn câu trong bài để chứng minh].\\n❌ Các đáp án sai:\\n- [B]: [Lý do sai].\\n- [C]: [Lý do sai].\\n- [D]: [Lý do sai]. (LƯU Ý: Phải dùng kí tự '\\n' để xuống dòng)",
                      "knowledgeName": "Tên kỹ năng bằng Tiếng Việt (VD: Tìm ý chính)",
                      "knowledgeType": "PHẢI LÀ '%s' hoặc 1 trong các enum Reading",
                      "targetWord": null
                    }
                    // ... 4 câu nữa
                  ]
                },
                {
                  "passageContent": "Nội dung bài đọc 2...",
                  "questions": [ /* 5 câu hỏi tương tự */ ]
                }
              ]
            }
            """, specificType.name(), level.name(), specificType.name(), allowedReadingEnums, specificType.name());
    }

    // =================================================================================================
    // CÁC HÀM PROMPT CŨ (GIỮ NGUYÊN)
    // =================================================================================================

    public static String buildToeicPart5Prompt(Level level, KnowledgeType specificType, String targetWord, int quantity) {
        boolean isDailySpaced = (specificType != null && targetWord != null);
        String allowedEnums = getPart5AllowedEnums();

        String conditionPrompt = isDailySpaced
                ? String.format("- You MUST generate exactly ONE question that explicitly tests the specific item: '%s' with KnowledgeType: '%s'. The 'correctAnswer' must be this word.", targetWord, specificType.name())
                : "- Distribute questions evenly between Grammar and Vocabulary items.";

        return String.format("""
            You are an expert ETS TOEIC test creator. Generate exactly %d incomplete sentences for TOEIC Part 5 at English difficulty level: %s.
            
            Strict constraints:
            %s
            - Total questions: Exactly %d.
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
            """, quantity, level.name(), conditionPrompt, quantity, allowedEnums);
    }

    public static String buildToeicPart6Prompt(Level level, KnowledgeType sm2Type, String sm2TargetWord) {
        boolean hasSpacedItem = (sm2Type != null && sm2TargetWord != null);
        String allowedEnums = getPart6AllowedEnums();

        String coreMissionPrompt = hasSpacedItem
                ? String.format("""
                - index [0] (Question 1 / Blank [1]): MUST strictly test the word/grammar item: '%s' with knowledgeType: '%s'. The 'correctAnswer' must be this word/option.
                """, sm2TargetWord, sm2Type.name())
                : """
                - index [0] (Question 1 / Blank [1]): Decide whether to test a Grammar rule OR a Vocabulary word. Assign the correct 'knowledgeType' accordingly.
                """;

        return String.format("""
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
    }

    public static String buildToeicPart7SinglePrompt(Level level, String synonymTargetWord) {
        return buildToeicPart7Prompt(
                level,
                synonymTargetWord,
                """
            You are an expert ETS TOEIC test creator. Generate EXACTLY 1 Single Passage (e.g., an email or advertisement) for TOEIC Part 7 at difficulty level: %s.
            """,
                """
            - index [1], [2], and [3] (Questions 2, 3, 4): Must test reading sub-skills. Choose uniquely from: [MAIN_IDEA, AUTHOR_PURPOSE, SPECIFIC_DETAILS, INFERENCE, NOT_TRUE_QUESTION, SENTENCE_INSERTION]. Their 'targetWord' fields must be null.
            """,
                4
        );
    }

    public static String buildToeicPart7MultiplePrompt(Level level, String synonymTargetWord) {
        return buildToeicPart7Prompt(
                level,
                synonymTargetWord,
                """
            You are an expert ETS TOEIC test creator. Generate EXACTLY 1 Double/Triple Passage set (related business texts) for TOEIC Part 7 at difficulty level: %s.
            Separate the texts in the 'passageContent' explicitly using markers like "--- TEXT 1 ---" and "--- TEXT 2 ---".
            """,
                """
            - index [1] (Question 2): MUST be a 'CROSS_REFERENCING' question (requiring the user to synthesize facts scattered across BOTH text 1 and text 2 to reach the answer). 'targetWord' must be null.
            - index [2], [3], and [4] (Questions 3, 4, 5): Must test reading sub-skills. Choose uniquely from: [MAIN_IDEA, AUTHOR_PURPOSE, SPECIFIC_DETAILS, INFERENCE, NOT_TRUE_QUESTION]. Their 'targetWord' fields must be null.
            """,
                5
        );
    }

    private static String buildToeicPart7Prompt(
            Level level, String synonymTargetWord, String introTemplate, String indexRules, int totalQuestions) {
        boolean hasSpacedItem = (synonymTargetWord != null);
        String allowedEnums = getPart7AllowedEnums();

        String coreMissionPrompt = hasSpacedItem
                ? String.format("""
                - index [0] (Question 1): MUST be a 'SYNONYM' question testing the contextual meaning of the specific word: '%s' appearing in the passage text. The 'targetWord' field must be '%s'.
                """, synonymTargetWord, synonymTargetWord)
                : """
                - index [0] (Question 1): You must pick one word from the text and create a 'SYNONYM' question. Provide the tested word in 'targetWord'.
                """;

        String intro = String.format(introTemplate, level.name());

        return String.format("""
            %s
            
            The architecture of the "questions" array MUST strictly follow this index-based rule (Total %d questions):
            %s
            %s
            
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
              ]
            }
            """, intro, totalQuestions, coreMissionPrompt, indexRules, allowedEnums);
    }

    public static String buildGeneralTestPrompt(Level level) {
        String targetLevel = level.toString();
        String allowedEnums = getAllowedKnowledgeTypes();
        return String.format(
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
    }

    public static String buildDailyRefillPrompt(String promptRequirement) {
        String allowedEnums = getAllowedKnowledgeTypes();
        return "Đóng vai chuyên gia ngôn ngữ Anh. Kho dữ liệu đang thiếu câu hỏi ôn tập. " +
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
    }

    public static String buildVipEntertainmentPrompt(List<String> wordList) {
        String wordsStr = String.join(", ", wordList);
        int wordCount = wordList.size();

        return String.format("""
            Bạn là một Bậc thầy kể chuyện (Master Storyteller) kiêm Nhà tiên tri hệ "chữa lành".
            DANH SÁCH TỪ VỰNG CẦN ÔN TẬP HÔM NAY (%d từ): %s

            NHIỆM VỤ CỦA BẠN DỰA TRÊN SỐ LƯỢNG TỪ:
            1. NẾU DANH SÁCH CHỈ CÓ 1 TỪ: Chỉ tạo MỘT câu dự đoán Tarot sử dụng từ đó. Cấu trúc "story" trong JSON PHẢI LÀ null.
            2. NẾU DANH SÁCH CÓ 2 TỪ TRỞ LÊN: 
               - Tự do phân tích và chọn ra MỘT từ phù hợp nhất trong danh sách (mang ý nghĩa tiên đoán, cảm xúc, chữa lành...) để làm câu Tarot.
               - Sử dụng TẤT CẢ các từ CÒN LẠI để tạo MỘT câu chuyện ngắn. Mỗi từ còn lại phải được sử dụng làm một câu đục lỗ (has_blank = true) trong truyện.

            ĐẠO LUẬT THÉP VỀ ĐẦU RA:
            - Trả về DUY NHẤT một chuỗi JSON hợp lệ, bắt đầu bằng { và kết thúc bằng }.
            - KHÔNG bọc JSON trong markdown (như ```json). KHÔNG thêm văn bản nào ngoài JSON.
            - Phải đảm bảo đóng ngoặc chính xác tuyệt đối.

            CẤU TRÚC JSON BẮT BUỘC (Copy y hệt format này):
            {
              "tarot": {
                "target_word": "từ được chọn làm Tarot",
                "word_meaning": "Nghĩa tiếng Việt ngắn gọn của từ đúng",
                "english_sentence": "Một câu tiên đoán tích cực có chứa từ đúng, nhưng thay từ đúng bằng [blank].",
                "options": ["từ đúng", "từ nhiễu 1", "từ nhiễu 2", "từ nhiễu 3"],
                "vietnamese_translation": "Bản dịch tiếng Việt HOÀN CHỈNH VÀ TỰ NHIÊN của câu tiên đoán (BẮT BUỘC DỊCH LUÔN TỪ CẦN ĐIỀN SANG TIẾNG VIỆT, KHÔNG ĐƯỢC ĐỂ LẠI CHỮ [blank] HAY TỪ TIẾNG ANH NÀO)."
              },
              "story": // NẾU CHỈ CÓ 1 TỪ TRONG DANH SÁCH, TRƯỜNG NÀY BẮT BUỘC LÀ null. NẾU CÓ > 1 TỪ, TẠO OBJECT NHƯ DƯỚI ĐÂY:
              {
                "title": "Tên câu chuyện BẰNG TIẾNG ANH (Tuyệt đối không dùng tiếng Việt. VD: The Quantum Librarian, The Silent Forest...)",
                "genre": "Thể loại (VD: Sci-Fi, Healing, Fairy Tale...)",
                "sentences": [
                  {
                    "english_sentence": "Câu tiếng anh (Nếu chứa từ vựng cần kiểm tra thì thay từ đó bằng [blank])",
                    "has_blank": true, // true nếu đục lỗ, false nếu câu dẫn truyện
                    "target_word": "từ đúng từ danh sách", // Nếu has_blank là false thì để null
                    "word_meaning": "Nghĩa tiếng Việt ngắn gọn của từ đúng", // Nếu has_blank là false thì để null
                    "options": ["từ đúng", "từ nhiễu 1", "từ nhiễu 2", "từ nhiễu 3"], // Nếu has_blank là false thì để []
                    "hint_translation": "Bản dịch tiếng Việt NHƯNG GIỮ NGUYÊN CHỮ [blank] (Dùng làm gợi ý). Ví dụ: 'Hệ thống yêu cầu được [blank] AI mới.' (Nếu has_blank = false thì để null)",
                    "full_translation": "Bản dịch tiếng Việt HOÀN CHỈNH VÀ TỰ NHIÊN (Tuyệt đối không chêm tiếng Anh hay [blank] vào). Ví dụ: 'Hệ thống yêu cầu được gặp gỡ AI mới.'"
                  },
                  {
                    "english_sentence": "Câu dẫn chuyện để kết nối logic.",
                    "has_blank": false,
                    "target_word": null,
                    "word_meaning": null,
                    "options": [],
                    "hint_translation": null,
                    "full_translation": "Bản dịch tiếng Việt đầy đủ."
                  }
                ]
              }
            }
            """, wordCount, wordsStr);
    }

    // =================================================================================================
    // 🚀 BỘ TỪ ĐIỂN CHUẨN HÓA DÀNH RIÊNG CHO WRITING PART 1
    // =================================================================================================
    public static String getWritingPart1GrammarRules() {
        return """
            [
              {
                "knowledgeType": "COORDINATING_CONJUNCTIONS",
                "knowledgeName": "Liên từ kết hợp (FANBOYS)"
              },
              {
                "knowledgeType": "SUBORDINATING_CONJUNCTIONS",
                "knowledgeName": "Liên từ phụ thuộc"
              },
              {
                "knowledgeType": "ADVERBIAL_CLAUSES",
                "knowledgeName": "Mệnh đề trạng ngữ"
              },
              {
                "knowledgeType": "RELATIVE_CLAUSES",
                "knowledgeName": "Mệnh đề quan hệ"
              },
              {
                "knowledgeType": "PREPOSITIONS_PLACE",
                "knowledgeName": "Giới từ chỉ vị trí / phương hướng"
              },
              {
                "knowledgeType": "CORRELATIVE_CONJUNCTIONS",
                "knowledgeName": "Cấu trúc tương quan"
              },
              {
                "knowledgeType": "ADVERBS",
                "knowledgeName": "Trạng từ chỉ thể cách"
              },
              {
                "knowledgeType": "PRESENT_CONTINUOUS",
                "knowledgeName": "Thì Hiện tại tiếp diễn"
              },
              {
                "knowledgeType": "PRESENT_SIMPLE",
                "knowledgeName": "Thì Hiện tại đơn"
              },
              {
                "knowledgeType": "PASSIVE_VOICE",
                "knowledgeName": "Câu bị động"
              },
              {
                "knowledgeType": "WORD_FORMATION",
                "knowledgeName": "Cấu tạo từ"
              },
              {
                "knowledgeType": "ARTICLES",
                "knowledgeName": "Mạo từ"
              },
              {
                "knowledgeType": "SUBJECT_VERB_AGREEMENT",
                "knowledgeName": "Sự hòa hợp Chủ - Vị"
              }
            ]
            """;
    }

    // =================================================================================================
    // 🚀 PROMPT CHO WRITING TEST ĐẦU VÀO (CHẤM ĐIỂM TỰ LUẬN BẰNG GEMINI VISION)
    // =================================================================================================
    public static String buildWritingPart1TestGradingPrompt() {
        // Truyền examIntro = "" để nội dung chỉ dẫn cho AI giữ nguyên 100% như bản gốc
        return buildWritingPart1GradingPrompt(
                "",
                """
                - Thí sinh được quyền dùng BẤT KỲ ngữ pháp nào họ muốn, miễn là mô tả đúng bức ảnh và sử dụng ĐỦ các từ khóa (givenWords). Tuyệt đối KHÔNG ép thí sinh phải dùng một ngữ pháp cố định. Nếu đề bài truyền lên 'requiredGrammar', hãy phớt lờ nó, tuyệt đối không dùng nó làm tiêu chí chấm.""",
                "");
    }

    // =================================================================================================
    // 🚀 PROMPT CHO WRITING PRACTICE HÀNG NGÀY (CHẤM ĐIỂM TỰ LUẬN BẰNG GEMINI VISION)
    // Khác với Placement Test: câu bốc từ SM-2 Sẽ có 'requiredGrammar' thật và BẮT BUỘC phải dùng đúng,
    // dùng sai thì phạt 0 điểm. Câu bốc ngẫu nhiên sẽ có 'requiredGrammar' là null nên chấm tự do.
    // =================================================================================================
    public static String buildWritingPart1PracticeGradingPrompt() {
        return buildWritingPart1GradingPrompt(
                "Đây là phần luyện tập hàng ngày (Practice). Một số câu được hệ thống bốc theo đúng điểm yếu ngữ pháp của bạn.",
                """
                - Xét theo ngữ pháp bắt buộc như sau:
                  + Nếu 'requiredGrammar' là null: thí sinh được quyền dùng BẤT KỲ ngữ pháp nào họ muốn, miễn là mô tả đúng bức ảnh và sử dụng ĐỦ các từ khóa (givenWords). Tuyệt đối KHÔNG tự ý thêm ngữ pháp bắt buộc nào.
                  + Nếu 'requiredGrammar' KHÁC null: đây là câu luyện đúng điểm yếu, thí sinh BẮT BUỘC phải dùng đúng cấu trúc ngữ pháp đó trong câu của họ. Nếu câu viết KHÔNG dùng đúng cấu trúc này (kể cả khi ngữ pháp còn lại đều đúng và mô tả đúng tranh) thì BẮT BUỘC cho ĐIỂM 0, tuyệt đối không cho điểm 1, 2 hay 3.
                  + Nếu 'requiredGrammar' khác null và bạn cho điểm 0, phải nói rõ trong 'feedback' rằng thí sinh đã KHÔNG dùng đúng cấu trúc ngữ pháp bắt buộc nào (kèm tên cấu trúc).""",
                """
                3. PHẦN WEAKNESS KHI BỊ ÉP 0 ĐIỂM: Nếu câu bị 0 điểm vì không dùng đúng 'requiredGrammar', hãy đặt 'knowledgeType' và 'knowledgeName' bằng đúng cặp giá trị của 'requiredGrammar' đó (chép từ cẩm nang bên dưới). Điều này giúp hệ thống đẩy lại đúng cấu trúc ngữ pháp vào vòng lặp luyện tập tiếp theo.""");
    }

    /**
     * Thân chung cho cả Placement Test và Practice.
     * Ba tham số cho phép mỗi bên thay đúng phần cần khác nhau, phần còn lại giữ nguyên chung.
     *
     * @param examIntro   Câu giới thiệu ngữ cảnh. Truyền "" để giữ nguyên prompt gốc.
     * @param grammarRule Khối quy tắc xử lý 'requiredGrammar'.
     * @param extraRules  Quy tắc bổ sung riêng (truyền "" nếu không có).
     */
    private static String buildWritingPart1GradingPrompt(
            String examIntro, String grammarRule, String extraRules) {
        String dictionary = getWritingPart1GrammarRules();

        return String.format("""
            Đóng vai một giáo viên chấm thi TOEIC Writing Part 1 cực kỳ khắt khe và tận tâm.
            Tôi sẽ gửi cho bạn một mảng JSON (inputData) chứa danh sách các câu trả lời của thí sinh. MỖI OBJECT CÓ CHỨA CÁC TỪ KHÓA BẮT BUỘC DÙNG (givenWords).
            %s

            TIÊU CHÍ CHẤM ĐIỂM (Thang 0 - 3 điểm) - ĐẠO LUẬT THÉP VỀ SỰ TỰ DO NGỮ PHÁP:
            %s
            - Điểm 0: Không viết gì, viết những thứ vô nghĩa, sai hoàn toàn ngữ cảnh tranh, hoặc bỏ sót bất kỳ từ khóa nào trong phần 'givenWords'.
            - Điểm 1: Dùng đủ từ khóa, đúng ý tranh nhưng MẮC CÁC LỖI NGỮ PHÁP NẶNG, NGHIÊM TRỌNG (sai trật tự từ cơ bản, thiếu động từ chính, sai sự hòa hợp chủ vị quá rõ ràng).
            - Điểm 2: Đúng ý tranh, đúng từ khóa, nhưng câu văn vụng về, mắc lỗi ngữ pháp nhẹ (sai mạo từ, sai giới từ nhẹ, chia thì chưa thật chuẩn xác nhưng vẫn hiểu được).
            - Điểm 3: Đạt điểm tối đa. Dùng đủ từ khóa, đúng ngữ pháp, mô tả bức ảnh hợp lý và tự nhiên.

            QUY TRÌNH PHÂN TÍCH VÀ ĐƯA FEEDBACK:
            1. PHẦN FEEDBACK: Nếu điểm DƯỚI 3, phân tích chi tiết lỗi sai của thí sinh. Cung cấp một "Câu gợi ý sửa lại" hoàn chỉnh ở cuối feedback.
            2. PHẦN WEAKNESS (RẤT QUAN TRỌNG): Nếu câu bị Điểm 0, 1 hoặc 2 do lỗi ngữ pháp, AI tự động chẩn đoán xem thí sinh đang yếu mảng ngữ pháp nào nhất dựa trên câu họ viết sai. So sánh lỗi đó với Cẩm nang Ngữ pháp dạng JSON dưới đây để LẤY CHÍNH XÁC cặp 'knowledgeType' và 'knowledgeName' tương ứng:
            %s
            --- BẮT ĐẦU CẨM NANG NGỮ PHÁP ---
            %s
            --- KẾT THÚC CẨM NANG NGỮ PHÁP ---
            %s
            ĐẦU RA BẮT BUỘC:
            Trả về DUY NHẤT một mảng JSON (JSON Array), KHÔNG bọc mã markdown (như ```json).
            Mỗi object trong mảng trả về BẮT BUỘC phải giữ lại chính xác 'questionId' mà tôi gửi trong inputData.
            Cấu trúc object trả về phải như sau:
            {
              "questionId": [Giữ nguyên ID từ inputData],
              "score": 2,
              "feedback": "Nhận xét chi tiết các lỗi sai: [Viết câu gợi ý hoàn hảo vào đây].",
              "weakness": {
                 "knowledgeType": "COPY CHÍNH XÁC MỘT GIÁ TRỊ TỪ CẨM NANG TRÊN (Ví dụ: ARTICLES). NẾU ĐIỂM 3, HOẶC LỖI SAI LÀ DO THIẾU TỪ KHÓA CHỨ KHÔNG PHẢI LỖI NGỮ PHÁP THÌ BẮT BUỘC ĐỂ null.",
                 "knowledgeName": "COPY CHÍNH XÁC TÊN TIẾNG VIỆT TỪ CẨM NANG TRÊN TƯƠNG ỨNG VỚI TYPE ĐÓ. ĐỂ null NẾU KNOWLEDGETYPE LÀ null."
              }
            }
            """, examIntro, grammarRule, extraRules, dictionary, "");
    }

    // =================================================================================================
    // 🚀 PROMPT CHO AI ĐỂ TỰ ĐỘNG SINH ĐỀ THI TEST WRITING PART 1
    // =================================================================================================
    public static String buildWritingPart1TestQuestionGenerationPrompt(Level level, int quantity) {
        String grammarRules = getWritingPart1GrammarRules();

        return String.format("""
            Đóng vai chuyên gia ra đề thi TOEIC Writing Part 1. Nhiệm vụ của bạn là tạo ra ĐÚNG %d câu hỏi cho bài kiểm tra đầu vào (Placement Test) của thí sinh trình độ %s.
            
            QUY TRÌNH TƯ DUY BẮT BUỘC ĐỂ ĐẢM BẢO SỰ HỢP LÝ:
            Bước 1: Chọn ngẫu nhiên ĐÚNG MỘT 'knowledgeType' trong Cẩm nang ngữ pháp dưới đây để làm tag phân loại (requiredGrammar).
            Bước 2: Tưởng tượng ra một bối cảnh bức ảnh (imagePrompt) và 2 từ khóa (givenWords) SAO CHO RẤT DỄ ĐỂ ĐẶT CÂU THEO NGỮ PHÁP ĐÃ CHỌN Ở BƯỚC 1.
            (Ví dụ: Nếu chọn 'RELATIVE_CLAUSES', hãy tạo ảnh có 1 người nổi bật giữa đám đông kèm từ khóa 'woman, hold' để thí sinh dễ viết 'The woman who is holding...').
            
            YÊU CẦU ĐẦU RA CHO MỖI OBJECT:
            1. 'requiredGrammar': Mã knowledgeType đã chọn ở Bước 1.
            2. 'givenWords': ĐÚNG 2 từ khóa (động từ, danh từ, trạng từ hoặc giới từ).
            3. 'imagePrompt': Mô tả ảnh cực chi tiết bằng TIẾNG ANH (dùng làm prompt cho AI vẽ tranh). Bức tranh phải khớp hoàn hảo với 2 từ khóa và ngữ pháp mục tiêu.
            
            CẨM NANG NGỮ PHÁP CHO PHÉP:
            %s
            
            ĐẦU RA BẮT BUỘC: 
            Trả về DUY NHẤT một mảng JSON (JSON Array), KHÔNG bọc mã markdown.
            [
              {
                "requiredGrammar": "RELATIVE_CLAUSES",
                "givenWords": "woman, hold",
                "imagePrompt": "A photorealistic image of a business woman holding a red folder, standing in front of other seated colleagues in a meeting room..."
              }
            ]
            """, quantity, level.name(), grammarRules);
    }

    // =================================================================================================
    // 🚀 PROMPT CHO AI SINH ĐỀ LUYỆN WRITING PART 1 THEO ĐÚNG 1 NGỮ PHÁP MỤC TIÊU
    // Dùng cho job quét SM-2: ép AI ra đề xoáy vào điểm ngữ pháp user đang yếu.
    // Khác bản Test ở chỗ KHÔNG cho AI tự chọn ngữ pháp, mà ép đúng 'targetGrammar'.
    // =================================================================================================
    public static String buildWritingPart1GrammarQuestionGenerationPrompt(Level level, String targetGrammar, int quantity) {
        String grammarRules = getWritingPart1GrammarRules();

        return String.format("""
            Đóng vai chuyên gia ra đề luyện TOEIC Writing Part 1. Nhiệm vụ của bạn là tạo ra ĐÚNG %d câu hỏi cho thí sinh trình độ %s.
            
            TRỌNG TÂM BẮT BUỘC: Tất cả %d câu hỏi phải xoáy vào ĐÚNG MỘT chủ điểm ngữ pháp duy nhất là: '%s'.
            Bối cảnh ảnh (imagePrompt) và 2 từ khóa (givenWords) của mỗi câu phải được thiết kế sao cho thí sinh BUỘC PHẢI dùng cấu trúc ngữ pháp '%s' mới diễn đạt được tự nhiên.
            Tuyệt đối không ra đề lệch sang ngữ pháp khác.
            
            YÊU CẦU ĐẦU RA CHO MỖI OBJECT:
            1. 'givenWords': ĐÚNG 2 từ khóa (động từ, danh từ, trạng từ hoặc giới từ).
            2. 'imagePrompt': Mô tả ảnh cực chi tiết bằng TIẾNG ANH (dùng làm prompt cho AI vẽ tranh). Bức tranh phải khớp hoàn hảo với 2 từ khóa và ngữ pháp mục tiêu.
            
            CẨM NANG NGỮ PHÁP THAM KHẢO (để bạn hiểu đúng bản chất của '%s'):
            %s
            
            ĐẦU RA BẮT BUỘC: 
            Trả về DUY NHẤT một mảng JSON (JSON Array), KHÔNG bọc mã markdown.
            [
              {
                "givenWords": "woman, hold",
                "imagePrompt": "A photorealistic image of a business woman holding a red folder in a meeting room..."
              }
            ]
            """, quantity, level.name(), quantity, targetGrammar, targetGrammar, targetGrammar, grammarRules);
    }
}