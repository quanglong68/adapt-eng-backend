package com.longdq.adaptengbackend.modules.premium.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.longdq.adaptengbackend.modules.premium.dto.DeepDiveAiDto;
import com.longdq.adaptengbackend.common.ai.AIPromptTemplates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import com.longdq.adaptengbackend.common.ai.AIService;
import com.longdq.adaptengbackend.common.enums.DeepDiveStatus;
import com.longdq.adaptengbackend.common.enums.KnowledgeType;
import com.longdq.adaptengbackend.common.enums.Level;
import com.longdq.adaptengbackend.common.enums.NotificationType;
import com.longdq.adaptengbackend.common.enums.QuestionType;
import com.longdq.adaptengbackend.modules.notification.entity.Notification;
import com.longdq.adaptengbackend.modules.notification.service.NotificationService;
import com.longdq.adaptengbackend.modules.premium.entity.DeepDiveQuestion;
import com.longdq.adaptengbackend.modules.premium.entity.DeepDiveSession;
import com.longdq.adaptengbackend.modules.premium.entity.DeepDiveSet;
import com.longdq.adaptengbackend.modules.premium.repository.DeepDiveSessionRepository;
import com.longdq.adaptengbackend.modules.premium.repository.DeepDiveSetRepository;
import com.longdq.adaptengbackend.modules.premium.repository.UserDeepDiveHistoryRepository;
import com.longdq.adaptengbackend.modules.spacedrepetition.entity.KnowledgeItem;
import com.longdq.adaptengbackend.modules.spacedrepetition.repository.KnowledgeItemRepository;
import com.longdq.adaptengbackend.modules.user.entity.User;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeepDiveAiWorker {

    private final DeepDiveSessionRepository sessionRepository;
    private final NotificationService notificationService;
    private final KnowledgeItemRepository knowledgeItemRepository;

    // Inject các kho dữ liệu VIP MỚI
    private final DeepDiveSetRepository deepDiveSetRepository;
    private final UserDeepDiveHistoryRepository historyRepository;
    private final AIService aiService;
    private final ObjectMapper objectMapper;

    private boolean isReadingType(KnowledgeType type) {
        if (type == null) return false;
        return type == KnowledgeType.SYNONYM || type == KnowledgeType.MAIN_IDEA ||
                type == KnowledgeType.AUTHOR_PURPOSE || type == KnowledgeType.SPECIFIC_DETAILS ||
                type == KnowledgeType.INFERENCE || type == KnowledgeType.CROSS_REFERENCING ||
                type == KnowledgeType.NOT_TRUE_QUESTION || type == KnowledgeType.SENTENCE_INSERTION;
    }

    private boolean isVocabularyType(KnowledgeType type) {
        if (type == null) return false;
        return type == KnowledgeType.VOCABULARY || type == KnowledgeType.COLLOCATIONS ||
                type == KnowledgeType.PHRASAL_VERBS || type == KnowledgeType.WORD_FORMATION;
    }

    @Async
    @Transactional
    public void generateQuestionsAsync(UUID sessionId, UUID userId, UUID knowledgeItemId, String targetWord, Level level) {
        log.info("Bắt đầu tiến trình ngầm sinh đề Deep Dive cho: {} ở Level: {}", targetWord, level);

        try {
            KnowledgeItem ki = knowledgeItemRepository.findById(knowledgeItemId).orElseThrow();
            KnowledgeType type = ki.getKnowledgeType();

            // 1. THUẬT TOÁN TÁI SỬ DỤNG (CHECK LỊCH SỬ)
            List<DeepDiveSet> existingSets = deepDiveSetRepository.findMatchingSets(type, targetWord, level);
            DeepDiveSet chosenSet = null;

            for (DeepDiveSet set : existingSets) {
                // Check xem user này đã từng làm cái bộ đề này chưa
                if (!historyRepository.existsByUserIdAndDeepDiveSetId(userId, set.getId())) {
                    chosenSet = set;
                    log.info("♻️ Tái sử dụng Bộ đề VIP (Set ID: {}) cho User: {}", set.getId(), userId);
                    break;
                }
            }

            // 2. GỌI AI NẾU KHÔNG CÓ BỘ ĐỀ NÀO PHÙ HỢP HOẶC ĐÃ LÀM HẾT RỒI
            if (chosenSet == null) {
                log.info("🤖 Gọi Gemini sinh Bộ đề Deep Dive mới cho User: {}", userId);
                chosenSet = generateNewSetFromAI(type, targetWord, level, ki);
            }

            // 3. LƯU ID CÂU HỎI VÀO SESSION CHỜ LÀM
            List<Long> questionIds = chosenSet.getQuestions().stream()
                    .map(DeepDiveQuestion::getId)
                    .collect(Collectors.toList());

            DeepDiveSession session = sessionRepository.findById(sessionId).orElseThrow();
            session.setQuestionsJson(objectMapper.writeValueAsString(questionIds));
            session.setStatus(DeepDiveStatus.READY);
            sessionRepository.save(session);

            // 4. BẮN NOTIFICATION REALTIME CHO USER (Giữ nguyên tính năng xịn của bác)
            Notification notif = Notification.builder()
                    .userId(userId)
                    .title("🎉 Đề chuyên sâu đã sẵn sàng!")
                    .message("AI đã thiết kế xong bài tập độc quyền cho '" + (targetWord != null ? targetWord : ki.getKnowledgeName()) + "'. Bấm vào làm ngay nhé!")
                    .type(NotificationType.AI_DEEP_DIVE)
                    .actionUrl("/toeic/test/deep-dive/" + sessionId)
                    .isRead(false)
                    .build();

            notificationService.sendNotification(userId, notif);

            log.info("✅ Đã chuẩn bị xong Đề VIP (Session: {})", sessionId);

        } catch (Exception e) {
            log.error("❌ Lỗi tiến trình ngầm Deep Dive: ", e);
            sessionRepository.findById(sessionId).ifPresent(s -> {
                s.setStatus(DeepDiveStatus.FAILED);
                sessionRepository.save(s);
            });
        }
    }

    // Hàm gọi AI và Map JSON sang Entity
    private DeepDiveSet generateNewSetFromAI(KnowledgeType type, String targetWord, Level level, KnowledgeItem ki) throws Exception {
        String prompt;
        boolean isReading = isReadingType(type);
        boolean isVocab = isVocabularyType(type);

        if (isReading) {
            prompt = AIPromptTemplates.buildDeepDiveReadingPrompt(level, type);
        } else if (isVocab) {
            prompt = AIPromptTemplates.buildDeepDiveVocabularyPrompt(level, type, targetWord);
        } else {
            prompt = AIPromptTemplates.buildDeepDiveGrammarPrompt(level, type);
        }

        String jsonResult = aiService.generateDeepDiveRawJson(prompt);
        if (jsonResult == null || jsonResult.isEmpty()) throw new RuntimeException("AI trả về rỗng!");

        objectMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);

        DeepDiveSet newSet = DeepDiveSet.builder()
                .knowledgeItemId(ki.getId())
                .knowledgeType(type)
                .targetWord(targetWord)
                .level(level)
                .build();

        List<DeepDiveQuestion> savedQuestions = new ArrayList<>();

        if (isReading) {
            DeepDiveAiDto.ReadingResponseDto readingDto = objectMapper.readValue(jsonResult, DeepDiveAiDto.ReadingResponseDto.class);
            for (DeepDiveAiDto.PassageDto pDto : readingDto.getPassages()) {
                for (DeepDiveAiDto.QuestionDto qDto : pDto.getQuestions()) {
                    savedQuestions.add(mapToEntity(qDto, newSet, pDto.getPassageContent()));
                }
            }
        } else {
            List<DeepDiveAiDto.QuestionDto> qDtos = objectMapper.readValue(jsonResult, new TypeReference<List<DeepDiveAiDto.QuestionDto>>() {});
            for (DeepDiveAiDto.QuestionDto qDto : qDtos) {
                savedQuestions.add(mapToEntity(qDto, newSet, null));
            }
        }

        newSet.setQuestions(savedQuestions);
        return deepDiveSetRepository.save(newSet);
    }

    private DeepDiveQuestion mapToEntity(DeepDiveAiDto.QuestionDto dto, DeepDiveSet set, String passage) {
        return DeepDiveQuestion.builder()
                .deepDiveSet(set)
                .passageContent(passage)
                .content(dto.getContent())
                .options(dto.getOptions())
                .correctAnswer(dto.getCorrectAnswer())
                .explanation(dto.getExplanation())
                .knowledgeName(dto.getKnowledgeName())
                .questionType(QuestionType.MULTIPLE_CHOICE)
                .build();
    }
}