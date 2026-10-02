package com.longdq.adaptengbackend.modules.writing.service;

import com.longdq.adaptengbackend.common.enums.Level;
import com.longdq.adaptengbackend.common.enums.ToeicPart;
import com.longdq.adaptengbackend.modules.spacedrepetition.entity.UserLearningProgress;
import com.longdq.adaptengbackend.modules.spacedrepetition.repository.UserLearningProgressRepository;
import com.longdq.adaptengbackend.modules.writing.dto.WritingPart23QuestionDto;
import com.longdq.adaptengbackend.modules.writing.entity.WritingQuestion;
import com.longdq.adaptengbackend.modules.writing.repository.WritingQuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Ráp session hỗn hợp 3×P1 + 1×P2 + 1×P3 từ các câu hỏi lẻ (anti-dup).
 * Dùng chung cho cả Test và Daily để tránh duplicate code.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WritingPart23SessionAssembler {

    private final WritingQuestionRepository questionRepository;
    private final UserLearningProgressRepository progressRepository;

    /**
     * Bốc 1 câu lẻ theo part + level, loại trừ câu user đã làm.
     * Thứ tự: câu chưa từng làm → câu làm lâu nhất (LRU) → random dự phòng.
     */
    public WritingQuestion pickOneQuestion(ToeicPart part, Level level, UUID userId, List<Long> pickedIds) {
        List<Long> safePicked = (pickedIds == null || pickedIds.isEmpty()) ? List.of(-1L) : pickedIds;

        Optional<WritingQuestion> qOpt = questionRepository.findNewWritingPart23Question(
                part.name(), level.name(), userId, safePicked);
        if (qOpt.isPresent()) {
            return qOpt.get();
        }

        qOpt = questionRepository.findLruWritingPart23Question(
                part.name(), level.name(), userId, safePicked);
        if (qOpt.isPresent()) {
            log.info("Kho {} level {} đã cạn câu mới, tái dùng câu làm lâu nhất cho user {}", part, level, userId);
            return qOpt.get();
        }

        List<WritingQuestion> fallback = questionRepository.findRandomByPartAndLevel(part.name(), level.name(), 5);
        for (WritingQuestion q : fallback) {
            if (!safePicked.contains(q.getId())) {
                log.warn("Fallback random {} level {} cho user {}", part, level, userId);
                return q;
            }
        }
        return fallback.isEmpty() ? null : fallback.get(0);
    }

    /**
     * Bốc 3 câu Part 1 (giữ nguyên hiển thị ảnh + givenWords, chấm tự do với requiredGrammar = null).
     */
    public List<WritingQuestion> pickPart1Questions(Level level, UUID userId, List<Long> pickedIds) {
        List<WritingQuestion> result = new ArrayList<>();
        List<Long> picked = new ArrayList<>(pickedIds);
        for (int i = 0; i < 3; i++) {
            WritingQuestion q = pickOneQuestion(ToeicPart.WRITING_PART_1, level, userId, picked);
            if (q == null) {
                break;
            }
            result.add(q);
            picked.add(q.getId());
        }
        return result;
    }

    /**
     * Quét chủ điểm SM-2 đến hạn của user cho 1 part (P2 tối đa 2, P3 tối đa 5).
     * Chỉ lấy mục có knowledgeItem + knowledgeType hợp lệ.
     */
    public List<String> loadDueConstraints(UUID userId, ToeicPart part, int limit) {
        List<UserLearningProgress> due = progressRepository
                .findByUserIdAndToeicPartAndNextReviewDateLessThanEqualOrderByNextReviewDateAsc(
                        userId, part, LocalDateTime.now());
        List<String> result = new ArrayList<>();
        for (UserLearningProgress item : due) {
            if (result.size() >= limit) {
                break;
            }
            if (item.getKnowledgeItem() == null || item.getKnowledgeItem().getKnowledgeType() == null) {
                continue;
            }
            result.add(item.getKnowledgeItem().getKnowledgeType().name());
        }
        return result;
    }

    /** Convert entity sang DTO session, gắn constraints cho P2/P3 (P1/Test = rỗng). */
    public WritingPart23QuestionDto toDto(WritingQuestion q, List<String> constraints) {
        WritingPart23QuestionDto dto = new WritingPart23QuestionDto();
        dto.setQuestionId(q.getId());
        dto.setToeicPart(q.getToeicPart() != null ? q.getToeicPart().name() : null);
        dto.setImageUrl(q.getImageUrl());
        dto.setGivenWords(q.getGivenWords());
        if (q.getKnowledgeItem() != null) {
            dto.setKnowledgeItemId(q.getKnowledgeItem().getId());
        }
        dto.setEmailFrom(q.getEmailFrom());
        dto.setEmailTo(q.getEmailTo());
        dto.setEmailDate(q.getEmailDate());
        dto.setEmailSubject(q.getEmailSubject());
        dto.setEmailBody(q.getEmailBody());
        dto.setDirections(q.getDirections());
        dto.setEssayType(q.getEssayType());
        dto.setEssayQuestion(q.getEssayQuestion());
        dto.setRequiredConstraints(constraints != null ? constraints : List.of());
        return dto;
    }
}
