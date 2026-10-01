package com.longdq.adaptengbackend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.longdq.adaptengbackend.entity.KnowledgeItem;
import com.longdq.adaptengbackend.entity.WritingQuestion;
import com.longdq.adaptengbackend.enums.KnowledgeType;
import com.longdq.adaptengbackend.enums.Level;
import com.longdq.adaptengbackend.enums.Purpose;
import com.longdq.adaptengbackend.enums.ToeicPart;
import com.longdq.adaptengbackend.repository.KnowledgeItemRepository;
import com.longdq.adaptengbackend.repository.WritingQuestionRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

            for (AiWritingQuestionDto dto : dtos) {
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
            }

            log.info("🎉 Hoàn tất sinh {} câu hỏi cho Level {}", dtos.size(), level);

        } catch (Exception e) {
            log.error("Lỗi dây chuyền xử lý ảnh Writing: {}", e.getMessage(), e);
            throw new RuntimeException("Lỗi khi đồng bộ dữ liệu câu hỏi Writing", e);
        }
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
}