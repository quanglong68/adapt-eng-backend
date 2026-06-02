package com.longdq.adaptengbackend.repository;

import com.longdq.adaptengbackend.entity.UserLearningProgress;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface UserLearningProgressRepository extends JpaRepository<UserLearningProgress, Long> {
    public List<UserLearningProgress> findByUserIdAndNextReviewDateLessThanEqual(UUID userId, LocalDateTime date, Pageable pageable);
    List<UserLearningProgress> findByUserIdAndRepetitionCountIn(
            UUID userId,
            List<Integer> repetitionCounts
    );

}
