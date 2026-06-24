package com.longdq.adaptengbackend.util;

import com.longdq.adaptengbackend.entity.Question;
import com.longdq.adaptengbackend.entity.User;
import com.longdq.adaptengbackend.entity.UserLearningProgress;
import com.longdq.adaptengbackend.enums.ToeicPart;
import com.longdq.adaptengbackend.repository.UserLearningProgressRepository;

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
        UserLearningProgress newProgress = new UserLearningProgress();
        newProgress.setUser(user);
        newProgress.setKnowledgeItem(question.getKnowledgeItem());
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
