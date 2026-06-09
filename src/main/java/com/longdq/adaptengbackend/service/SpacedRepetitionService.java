package com.longdq.adaptengbackend.service;

import com.longdq.adaptengbackend.entity.UserLearningProgress;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class SpacedRepetitionService {

    // Không cần inject UserLearningProgressRepository vào đây nữa để code nhẹ hơn.
    // Nếu bạn đang dùng nó ở hàm khác trong class này thì cứ giữ lại dòng @RequiredArgsConstructor nhé.

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

        // CHÌA KHÓA Ở ĐÂY: Chỉ trả về object đã được thay đổi thuộc tính trên RAM
        return progress;
    }
}