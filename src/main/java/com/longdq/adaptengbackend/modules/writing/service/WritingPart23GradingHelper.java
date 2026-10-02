package com.longdq.adaptengbackend.modules.writing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.longdq.adaptengbackend.common.enums.KnowledgeType;
import com.longdq.adaptengbackend.common.enums.Purpose;
import com.longdq.adaptengbackend.common.enums.ToeicPart;
import com.longdq.adaptengbackend.modules.spacedrepetition.entity.KnowledgeItem;
import com.longdq.adaptengbackend.modules.spacedrepetition.entity.UserLearningProgress;
import com.longdq.adaptengbackend.modules.spacedrepetition.repository.KnowledgeItemRepository;
import com.longdq.adaptengbackend.modules.writing.dto.WritingPart23AiResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.longdq.adaptengbackend.modules.user.entity.User;

/**
 * Logic chấm P2/P3 dùng chung cho Test và Daily: parse AI, trừ phạt ở backend, lọc weaknesses.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WritingPart23GradingHelper {

    private final ObjectMapper objectMapper;
    private final KnowledgeItemRepository knowledgeItemRepository;

    /** Parse JSON AI trả về, null-safe. */
    public WritingPart23AiResultDto parseAiResult(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, WritingPart23AiResultDto.class);
        } catch (Exception e) {
            log.error("Lỗi parse JSON chấm P2/P3 từ AI", e);
            return null;
        }
    }

    /**
     * Tính điểm cuối: score gốc clamp [0,max] trừ 1 mỗi constraint fail + (P3) trừ 1 nếu thiếu từ.
     * Trả về số điểm phạt để ghi chú vào feedback.
     */
    public int applyPenalty(WritingPart23AiResultDto ai, List<String> required, int maxScore, boolean checkWordCount) {
        int base = ai != null && ai.getScore() != null ? ai.getScore() : 0;
        base = Math.max(0, Math.min(maxScore, base));
        int penalty = 0;
        if (required != null) {
            Map<String, Boolean> results = ai != null ? ai.getConstraintResults() : null;
            for (String req : required) {
                Boolean ok = results != null ? results.get(req) : null;
                if (!Boolean.TRUE.equals(ok)) {
                    penalty++;
                }
            }
        }
        if (checkWordCount && (ai == null || ai.getWordCount() == null || ai.getWordCount() < 300)) {
            penalty++;
        }
        return Math.max(0, base - penalty);
    }

    /** Đếm số lỗi phạt để ghi chú feedback (tách riêng để rõ ràng). */
    public String buildPenaltyNote(WritingPart23AiResultDto ai, List<String> required, boolean checkWordCount) {
        List<String> notes = new ArrayList<>();
        if (required != null && !required.isEmpty()) {
            Map<String, Boolean> results = ai != null ? ai.getConstraintResults() : null;
            for (String req : required) {
                Boolean ok = results != null ? results.get(req) : null;
                if (!Boolean.TRUE.equals(ok)) {
                    notes.add("chưa dùng đúng [" + req + "] (-1 điểm)");
                }
            }
        }
        if (checkWordCount && (ai == null || ai.getWordCount() == null || ai.getWordCount() < 300)) {
            notes.add("bài dưới 300 từ (-1 điểm)");
        }
        if (notes.isEmpty()) {
            return "";
        }
        return " [Hệ thống trừ điểm: " + String.join("; ", notes) + ".]";
    }

    /**
     * Lọc weaknesses: chỉ giữ enum hợp lệ, loại trùng constraints, giới hạn số lượng.
     */
    public List<KnowledgeType> filterWeaknesses(WritingPart23AiResultDto ai, List<String> required, int max) {
        List<String> raw = ai != null && ai.getWeaknesses() != null ? ai.getWeaknesses() : List.of();
        Set<String> excluded = new HashSet<>(required != null ? required : List.of());
        List<KnowledgeType> result = new ArrayList<>();
        for (String w : raw) {
            if (result.size() >= max) {
                break;
            }
            if (w == null || w.isBlank() || excluded.contains(w)) {
                continue;
            }
            try {
                result.add(KnowledgeType.valueOf(w));
            } catch (IllegalArgumentException e) {
                log.warn("AI trả về knowledgeType không hợp lệ: {}. Bỏ qua.", w);
            }
        }
        return result;
    }

    /** Tìm hoặc tạo KnowledgeItem PRACTICE cho 1 KnowledgeType (tái dùng logic Part 1 cũ). */
    public KnowledgeItem getOrCreateKnowledgeItem(KnowledgeType type) {
        return knowledgeItemRepository.findAll().stream()
                .filter(k -> k.getKnowledgeType() == type && k.getPurpose() == Purpose.PRACTICE)
                .findFirst()
                .orElseGet(() -> {
                    KnowledgeItem newKi = new KnowledgeItem();
                    newKi.setKnowledgeType(type);
                    newKi.setKnowledgeName("Writing Grammar: " + type.name());
                    newKi.setPurpose(Purpose.PRACTICE);
                    return knowledgeItemRepository.save(newKi);
                });
    }

    /** Tạo progress mới interval=1 cho điểm yếu mới phát hiện. */
    public UserLearningProgress newWeaknessProgress(User user, KnowledgeItem item, ToeicPart part) {
        UserLearningProgress progress = new UserLearningProgress();
        progress.setUser(user);
        progress.setKnowledgeItem(item);
        progress.setToeicPart(part);
        progress.setEaseFactor(2.5);
        progress.setIntervalDays(1);
        progress.setRepetitionCount(0);
        progress.setNextReviewDate(LocalDateTime.now().plusDays(1));
        return progress;
    }

    /** Đếm từ đơn giản ở backend để đối chiếu (AI vẫn là nguồn chính cho word_count). */
    public int countWords(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }
}
