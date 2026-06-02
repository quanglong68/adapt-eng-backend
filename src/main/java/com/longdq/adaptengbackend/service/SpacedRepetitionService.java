package com.longdq.adaptengbackend.service;

import com.longdq.adaptengbackend.entity.UserLearningProgress;
import com.longdq.adaptengbackend.repository.UserLearningProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;


@RequiredArgsConstructor
@Service
public class SpacedRepetitionService {

    private final UserLearningProgressRepository progressRepository;

    @Transactional
    public UserLearningProgress updateProgress(UserLearningProgress progress, boolean isCorrect) {
        if (isCorrect) {
            int currentRepetition = progress.getRepetitionCount();
            int nextInterval;
            if (currentRepetition == 0) {
                nextInterval = 1;
            } else if (currentRepetition == 1) {
                nextInterval = 6;
            } else {
                nextInterval = (int) Math.round(progress.getIntervalDays() * progress.getEaseFactor());
            }
            progress.setRepetitionCount(currentRepetition + 1);
            progress.setIntervalDays(nextInterval);
        } else {
            progress.setRepetitionCount(0);
            progress.setIntervalDays(1);

            double newEaseFactor = progress.getEaseFactor() - 0.54;
            if (newEaseFactor < 1.3) {
                newEaseFactor = 1.3;
            }
            progress.setEaseFactor(newEaseFactor);
        }

        LocalDateTime nextReviewDate = LocalDateTime.now().plusDays(progress.getIntervalDays());
        progress.setNextReviewDate(nextReviewDate);
        return progressRepository.save(progress);
    }

}
