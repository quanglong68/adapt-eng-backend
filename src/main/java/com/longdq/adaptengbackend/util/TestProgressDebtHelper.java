package com.longdq.adaptengbackend.util;

import com.longdq.adaptengbackend.entity.Question;
import com.longdq.adaptengbackend.entity.User;
import com.longdq.adaptengbackend.entity.UserLearningProgress;
import com.longdq.adaptengbackend.enums.ToeicPart;
import com.longdq.adaptengbackend.repository.UserLearningProgressRepository;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public final class TestProgressDebtHelper {

    private TestProgressDebtHelper() {
    }

    public static void recordWrongAnswerDebt(
            Question question,
            User user,
            Map<String, UserLearningProgress> progressCache,
            UserLearningProgressRepository progressRepository) {
        recordWrongAnswerDebt(question, user, progressCache, progressRepository, null);
    }

    public static void recordWrongAnswerDebt(
            Question question,
            User user,
            Map<String, UserLearningProgress> progressCache,
            UserLearningProgressRepository progressRepository,
            ToeicPart toeicPart) {

        UUID knowledgeId = question.getKnowledgeItem() != null ? question.getKnowledgeItem().getId() : null;
        String targetWord = question.getTargetWord();

        if (knowledgeId == null && targetWord == null) {
            return;
        }

        String cacheKey = ProgressCacheKeyUtil.buildKey(knowledgeId, targetWord);
        if (progressCache.containsKey(cacheKey)) {
            return;
        }

        UserLearningProgress progress = progressRepository
                .findProgressRecord(user.getId(), knowledgeId, targetWord)
                .orElseGet(() -> createNewTestDebtProgress(user, question, targetWord, toeicPart));

        progressCache.put(cacheKey, progress);
    }

    private static UserLearningProgress createNewTestDebtProgress(
            User user, Question question, String targetWord, ToeicPart toeicPart) {
        UserLearningProgress newProgress = new UserLearningProgress();
        newProgress.setUser(user);
        newProgress.setKnowledgeItem(question.getKnowledgeItem());
        newProgress.setTargetWord(targetWord);
        newProgress.setIntervalDays(0);
        newProgress.setRepetitionCount(0);
        newProgress.setEaseFactor(1.3);
        newProgress.setNextReviewDate(LocalDateTime.now().plusDays(1));
        if (toeicPart != null) {
            newProgress.setToeicPart(toeicPart);
        }
        return newProgress;
    }
}
