package com.longdq.adaptengbackend.service;

import com.longdq.adaptengbackend.annotation.RequirePremium;
import com.longdq.adaptengbackend.dto.SaveWordRequestDto;
import com.longdq.adaptengbackend.entity.User;
import com.longdq.adaptengbackend.entity.UserLearningProgress;
import com.longdq.adaptengbackend.exception.DuplicateResourceException;
import com.longdq.adaptengbackend.repository.UserLearningProgressRepository;
import com.longdq.adaptengbackend.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SaveWordService {

    private final UserLearningProgressRepository progressRepository;

    @RequirePremium
    @Transactional
    public UserLearningProgress saveWord(SaveWordRequestDto request) {
        User user = SecurityUtils.getCurrentUser();
        String word = request.getWord().trim();
        LocalDateTime now = LocalDateTime.now();

        progressRepository.findProgressRecord(user.getId(), null, word)
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("Từ \"" + word + "\" đã có trong danh sách ôn tập.");
                });

        UserLearningProgress progress = new UserLearningProgress();
        progress.setUser(user);
        progress.setTargetWord(word);
        progress.setRepetitionCount(0);
        progress.setEaseFactor(2.5);
        progress.setIntervalDays(1);
        progress.setNextReviewDate(now);
        progress.setLearningTrack(user.getLearningTrack());

        UserLearningProgress saved = progressRepository.save(progress);
        log.info("Saved word '{}' for premium user: {}", word, user.getEmail());
        return saved;
    }
}
