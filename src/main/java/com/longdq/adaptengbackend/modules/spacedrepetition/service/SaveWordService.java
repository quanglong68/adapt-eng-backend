package com.longdq.adaptengbackend.modules.spacedrepetition.service;

import com.longdq.adaptengbackend.common.security.RequirePremium;
import com.longdq.adaptengbackend.modules.spacedrepetition.dto.SaveWordRequestDto;
import com.longdq.adaptengbackend.modules.user.entity.User;
import com.longdq.adaptengbackend.modules.spacedrepetition.entity.UserLearningProgress;
import com.longdq.adaptengbackend.common.exception.DuplicateResourceException;
import com.longdq.adaptengbackend.modules.spacedrepetition.repository.UserLearningProgressRepository;
import com.longdq.adaptengbackend.common.security.SecurityUtils;
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
