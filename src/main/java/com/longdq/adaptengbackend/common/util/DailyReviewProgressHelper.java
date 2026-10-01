package com.longdq.adaptengbackend.common.util;

import com.longdq.adaptengbackend.modules.spacedrepetition.entity.KnowledgeItem;
import com.longdq.adaptengbackend.modules.toeic.entity.Question;
import com.longdq.adaptengbackend.modules.user.entity.User;
import com.longdq.adaptengbackend.modules.spacedrepetition.entity.UserLearningProgress;
import com.longdq.adaptengbackend.common.enums.ToeicPart;
import com.longdq.adaptengbackend.modules.spacedrepetition.repository.UserLearningProgressRepository;

import java.util.Map;
import java.util.UUID;

public final class DailyReviewProgressHelper {

    private DailyReviewProgressHelper() {
    }

    public static UserLearningProgress resolveOrCreateProgress(
            User user,
            Question question,
            Map<String, UserLearningProgress> progressCache,
            UserLearningProgressRepository progressRepository) {
        return resolveOrCreateProgress(user, question, progressCache, progressRepository, null);
    }

    public static UserLearningProgress resolveOrCreateProgress(
            User user,
            Question question,
            Map<String, UserLearningProgress> progressCache,
            UserLearningProgressRepository progressRepository,
            ToeicPart toeicPart) {

        UUID knowledgeId = question.getKnowledgeItem() != null ? question.getKnowledgeItem().getId() : null;
        String targetWord = question.getTargetWord();
        String cacheKey = ProgressCacheKeyUtil.buildKey(knowledgeId, targetWord);

        UserLearningProgress progress = progressCache.get(cacheKey);
        if (progress != null) {
            return progress;
        }

        progress = progressRepository.findProgressRecord(user.getId(), knowledgeId, targetWord)
                .orElseGet(() -> createNewDailyProgress(user, question, targetWord, toeicPart));

        progressCache.put(cacheKey, progress);
        return progress;
    }

    private static UserLearningProgress createNewDailyProgress(
            User user, Question question, String targetWord, ToeicPart toeicPart) {
        return createNewProgress(user, question.getKnowledgeItem(), targetWord, toeicPart);
    }

    /**
     * Overload dành riêng cho Writing: câu hỏi Writing nằm ở bảng writing_questions
     * nên không dùng được tham số Question của Reading. Tham số cuối luôn là null vì
     * Writing không có khái niệm targetWord (từ vựng), chỉ ôn ngữ pháp qua knowledgeItem.
     */
    public static UserLearningProgress resolveOrCreateProgress(
            User user,
            KnowledgeItem knowledgeItem,
            String targetWord,
            Map<String, UserLearningProgress> progressCache,
            UserLearningProgressRepository progressRepository,
            ToeicPart toeicPart) {

        UUID knowledgeId = knowledgeItem != null ? knowledgeItem.getId() : null;
        String cacheKey = ProgressCacheKeyUtil.buildKey(knowledgeId, targetWord);

        UserLearningProgress progress = progressCache.get(cacheKey);
        if (progress != null) {
            return progress;
        }

        progress = progressRepository.findProgressRecord(user.getId(), knowledgeId, targetWord)
                .orElseGet(() -> createNewProgress(user, knowledgeItem, targetWord, toeicPart));

        progressCache.put(cacheKey, progress);
        return progress;
    }

    private static UserLearningProgress createNewProgress(
            User user, KnowledgeItem knowledgeItem, String targetWord, ToeicPart toeicPart) {
        UserLearningProgress newProgress = new UserLearningProgress();
        newProgress.setUser(user);
        newProgress.setKnowledgeItem(knowledgeItem);
        newProgress.setTargetWord(targetWord);
        newProgress.setEaseFactor(2.5);
        newProgress.setRepetitionCount(0);
        newProgress.setIntervalDays(1);
        if (toeicPart != null) {
            newProgress.setToeicPart(toeicPart);
        }
        return newProgress;
    }
}
