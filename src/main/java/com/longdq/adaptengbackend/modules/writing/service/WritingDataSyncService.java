package com.longdq.adaptengbackend.modules.writing.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.longdq.adaptengbackend.modules.spacedrepetition.entity.KnowledgeItem;
import com.longdq.adaptengbackend.modules.writing.entity.WritingQuestion;
import com.longdq.adaptengbackend.common.enums.KnowledgeType;
import com.longdq.adaptengbackend.common.enums.Level;
import com.longdq.adaptengbackend.common.enums.Purpose;
import com.longdq.adaptengbackend.common.enums.ToeicPart;
import com.longdq.adaptengbackend.modules.spacedrepetition.repository.KnowledgeItemRepository;
import com.longdq.adaptengbackend.modules.writing.repository.WritingQuestionRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import com.longdq.adaptengbackend.common.ai.AIService;
import com.longdq.adaptengbackend.common.ai.CloudinaryService;
import com.longdq.adaptengbackend.common.ai.HuggingFaceService;

@Slf4j
@Service
@RequiredArgsConstructor
public class WritingDataSyncService {

    private final AIService aiService;
    private final HuggingFaceService huggingFaceService;
    private final CloudinaryService cloudinaryService;
    private final ObjectMapper objectMapper;
    private final WritingQuestionRepository writingQuestionRepository;
    private final KnowledgeItemRepository knowledgeItemRepository; // Thêm Repos này

    @Data
    public static class AiWritingQuestionDto {
        private String imagePrompt;
        private String givenWords;
        private String requiredGrammar;
    }

    @Transactional
    public void generateAndSaveWritingPart1Questions(Level level, int quantity) {
        log.info("BẮT ĐẦU: Nhờ Gemini sinh {} kịch bản Writing Part 1 cho Level {}", quantity, level);

        // 1. GỌI GEMINI NGHĨ KỊCH BẢN
        String jsonResult = aiService.generateWritingPart1Questions(level, quantity);

        if (jsonResult == null || jsonResult.isEmpty()) {
            throw new RuntimeException("Không lấy được dữ liệu kịch bản từ Gemini");
        }

        saveGeneratedQuestions(level, jsonResult, null);
    }

    /**
     * Sinh đề Writing Part 1 XOÁY ĐÚNG một KnowledgeItem (ngữ pháp user đang yếu).
     * Dùng cho job quét SM-2 hàng ngày. Khác luồng random ở chỗ ÉP KnowledgeItem
     * của mọi câu về đúng mục tiêu, không để AI tự chọn ngữ pháp.
     */
    @Transactional
    public void generateAndSaveWritingPart1Questions(Level level, KnowledgeItem targetKnowledgeItem, int quantity) {
        String targetGrammar = resolveTargetGrammar(targetKnowledgeItem);
        log.info("BẮT ĐẦU: Nhờ Gemini sinh {} câu Writing Part 1 cho Level {} theo ngữ pháp '{}'",
                quantity, level, targetGrammar);

        String jsonResult = aiService.generateWritingPart1Questions(level, targetGrammar, quantity);

        if (jsonResult == null || jsonResult.isEmpty()) {
            throw new RuntimeException("Không lấy được dữ liệu kịch bản Writing theo ngữ pháp từ Gemini");
        }

        saveGeneratedQuestions(level, jsonResult, targetKnowledgeItem);
    }

    /** Lấy mã ngữ pháp để nhồi vào prompt: ưu tiên knowledgeType (enum), fallback knowledgeName. */
    private String resolveTargetGrammar(KnowledgeItem targetKnowledgeItem) {
        if (targetKnowledgeItem == null) {
            return null;
        }
        return targetKnowledgeItem.getKnowledgeType() != null
                ? targetKnowledgeItem.getKnowledgeType().name()
                : targetKnowledgeItem.getKnowledgeName();
    }

    /**
     * Dùng chung cho cả 2 luồng sinh đề.
     * @param forcedKnowledgeItem nếu != null thì gắn cứng KnowledgeItem này cho mọi câu (job SM-2),
     *                            nếu null thì lấy theo requiredGrammar do AI trả về (luồng random).
     */
    private void saveGeneratedQuestions(Level level, String jsonResult, KnowledgeItem forcedKnowledgeItem) {
        try {
            objectMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
            List<AiWritingQuestionDto> dtos = objectMapper.readValue(
                    jsonResult, new TypeReference<List<AiWritingQuestionDto>>() {}
            );

            // Chịu lỗi từng câu: Space báo lỗi (ZeroGPU quota/timeout/treo) chỉ WARN + bỏ qua câu đó,
            // không để sập cả transaction seed đề. Chỉ ném lỗi khi không lưu được câu nào.
            int successCount = 0;
            for (AiWritingQuestionDto dto : dtos) {
                try {
                    log.info("Đang xử lý kịch bản: {}", dto.getImagePrompt());

                    // Bước A: Xác định KnowledgeItem gắn cho câu hỏi
                    KnowledgeItem kItem = forcedKnowledgeItem != null
                            ? forcedKnowledgeItem
                            : getOrCreateKnowledgeItem(dto.getRequiredGrammar());

                    // Bước B: Gửi prompt cho FLUX vẽ ảnh
                    byte[] imageBytes = huggingFaceService.generateImage(dto.getImagePrompt());

                    // Bước C: Up ảnh vừa vẽ lên Cloudinary
                    String permanentCloudUrl = cloudinaryService.uploadImage(imageBytes, "toeic_writing_part1");

                    // Bước D: Lưu vào Database
                    WritingQuestion question = new WritingQuestion();
                    question.setToeicPart(ToeicPart.WRITING_PART_1);
                    question.setLevel(level);
                    question.setGivenWords(dto.getGivenWords());
                    question.setKnowledgeItem(kItem); // GẮN ID THAY VÌ GẮN CHUỖI
                    question.setImageUrl(permanentCloudUrl);

                    writingQuestionRepository.save(question);
                    log.info("✅ THÀNH CÔNG! Link ảnh lưu trữ: {}", permanentCloudUrl);
                    successCount++;
                } catch (Exception itemError) {
                    log.warn("⚠️ Bỏ qua 1 kịch bản do lỗi vẽ/tải ảnh (không sập cả batch). Kịch bản: {}. Nguyên nhân gốc: {}",
                            dto.getImagePrompt(), rootCauseMessage(itemError));
                }
            }

            log.info("🎉 Hoàn tất sinh {} câu hỏi cho Level {} (thành công {}/{})",
                    dtos.size(), level, successCount, dtos.size());

            if (successCount == 0 && !dtos.isEmpty()) {
                throw new RuntimeException("Tất cả " + dtos.size()
                        + " kịch bản đều lỗi ở bước vẽ/tải ảnh, không lưu được câu nào. Xem các dòng WARN phía trên để biết nguyên nhân gốc.");
            }

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Lỗi dây chuyền xử lý ảnh Writing: {}", e.getMessage(), e);
            throw new RuntimeException("Lỗi khi đồng bộ dữ liệu câu hỏi Writing. Nguyên nhân gốc: "
                    + rootCauseMessage(e), e);
        }
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

    // Hàm phụ trợ giúp convert cái Chuỗi AI trả về thành 1 dòng trong bảng KnowledgeItem
    private KnowledgeItem getOrCreateKnowledgeItem(String typeStr) {
        if (typeStr == null || typeStr.isEmpty() || typeStr.equalsIgnoreCase("Không có")) {
            return null; // Trả về null nếu AI nhả bậy bạ
        }

        try {
            KnowledgeType type = KnowledgeType.valueOf(typeStr);

            // Tìm trong DB xem có chưa (Tái sử dụng Purpose.PRACTICE cho Writing)
            return knowledgeItemRepository.findAll().stream()
                    .filter(k -> k.getKnowledgeType() == type && k.getPurpose() == Purpose.PRACTICE)
                    .findFirst()
                    .orElseGet(() -> {
                        KnowledgeItem newKi = new KnowledgeItem();
                        newKi.setKnowledgeType(type);
                        newKi.setKnowledgeName("Writing Grammar: " + type.name()); // Có thể làm hàm dịch sau
                        newKi.setPurpose(Purpose.PRACTICE);
                        return knowledgeItemRepository.save(newKi);
                    });
        } catch (IllegalArgumentException e) {
            log.warn("AI trả về Type không hợp lệ: {}. Đã bỏ qua.", typeStr);
            return null; // Bỏ qua nếu AI ngáo đá tự bịa type
        }
    }

    @Data
    // Prompt yêu cầu AI trả key snake_case (email_metadata, email_body) nên phải gắn
    // SnakeCaseStrategy, nếu không Jackson sẽ lặng lẽ bỏ qua và entity bị lưu NULL.
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class AiWritingPart2Dto {
        private EmailMetadata emailMetadata;
        private String emailBody;
        private String directions;

        @Data
        @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
        public static class EmailMetadata {
            private String from;
            private String to;
            private String date;
            private String subject;
        }
    }

    @Data
    // Lý do như trên: AI trả essay_type (snake_case), field Java là essayType.
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class AiWritingPart3Dto {
        private String essayType;
        private String question;
        private String directions;
    }

    /**
     * Sinh 1 câu Part 2 generic (không ép ngữ pháp) và lưu vào kho.
     */
    @Transactional
    public void generateAndSaveWritingPart2Question(Level level) {
        log.info("BẮT ĐẦU: Nhờ Gemini sinh 1 câu Writing Part 2 cho Level {}", level);
        String jsonResult = aiService.generateWritingPart2Question(level);
        if (jsonResult == null || jsonResult.isBlank()) {
            throw new RuntimeException("Không lấy được đề Writing Part 2 từ Gemini");
        }
        try {
            AiWritingPart2Dto dto = objectMapper.readValue(jsonResult, AiWritingPart2Dto.class);
            WritingQuestion question = new WritingQuestion();
            question.setToeicPart(ToeicPart.WRITING_PART_2);
            question.setLevel(level);
            if (dto.getEmailMetadata() != null) {
                question.setEmailFrom(dto.getEmailMetadata().getFrom());
                question.setEmailTo(dto.getEmailMetadata().getTo());
                question.setEmailDate(dto.getEmailMetadata().getDate());
                question.setEmailSubject(dto.getEmailMetadata().getSubject());
            }
            question.setEmailBody(dto.getEmailBody());
            question.setDirections(dto.getDirections());
            writingQuestionRepository.save(question);
            log.info("Đã lưu 1 câu Writing Part 2 cho Level {}", level);
        } catch (Exception e) {
            log.error("Lỗi parse đề Writing Part 2: {}", e.getMessage(), e);
            throw new RuntimeException("Lỗi khi lưu câu hỏi Writing Part 2", e);
        }
    }

    /**
     * Sinh 1 câu Part 3 generic (không ép ngữ pháp) và lưu vào kho.
     */
    @Transactional
    public void generateAndSaveWritingPart3Question(Level level) {
        log.info("BẮT ĐẦU: Nhờ Gemini sinh 1 câu Writing Part 3 cho Level {}", level);
        String jsonResult = aiService.generateWritingPart3Question(level);
        if (jsonResult == null || jsonResult.isBlank()) {
            throw new RuntimeException("Không lấy được đề Writing Part 3 từ Gemini");
        }
        try {
            AiWritingPart3Dto dto = objectMapper.readValue(jsonResult, AiWritingPart3Dto.class);
            WritingQuestion question = new WritingQuestion();
            question.setToeicPart(ToeicPart.WRITING_PART_3);
            question.setLevel(level);
            question.setEssayType(dto.getEssayType());
            question.setEssayQuestion(dto.getQuestion());
            question.setDirections(dto.getDirections());
            writingQuestionRepository.save(question);
            log.info("Đã lưu 1 câu Writing Part 3 cho Level {}", level);
        } catch (Exception e) {
            log.error("Lỗi parse đề Writing Part 3: {}", e.getMessage(), e);
            throw new RuntimeException("Lỗi khi lưu câu hỏi Writing Part 3", e);
        }
    }
}