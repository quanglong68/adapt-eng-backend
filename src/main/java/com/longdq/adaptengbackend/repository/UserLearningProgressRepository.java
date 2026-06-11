package com.longdq.adaptengbackend.repository;

import com.longdq.adaptengbackend.entity.UserLearningProgress;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserLearningProgressRepository extends JpaRepository<UserLearningProgress, Long> {
    public List<UserLearningProgress> findByUserIdAndNextReviewDateLessThanEqual(UUID userId, LocalDateTime date, Pageable pageable);
    List<UserLearningProgress> findByUserIdAndRepetitionCountIn(
            UUID userId,
            List<Integer> repetitionCounts
    );
    List<UserLearningProgress> findByUserIdAndNextReviewDateLessThanEqualOrderByIntervalDaysAscNextReviewDateAsc(
            UUID userId,
            LocalDateTime currentDate
    );

    @Query("SELECT p FROM UserLearningProgress p WHERE p.user.id = :userId " +
            "AND ((:knowledgeItemId IS NULL AND p.knowledgeItem IS NULL) OR p.knowledgeItem.id = :knowledgeItemId) " +
            "AND ((:targetWord IS NULL AND p.targetWord IS NULL) OR p.targetWord = :targetWord)")
    Optional<UserLearningProgress> findProgressRecord(
            @Param("userId") UUID userId,
            @Param("knowledgeItemId") UUID knowledgeItemId,
            @Param("targetWord") String targetWord
    );

    @Query("SELECT DISTINCT p.knowledgeItem.id, p.knowledgeItem.knowledgeName, p.targetWord FROM UserLearningProgress p " +
            "WHERE p.nextReviewDate <= :thresholdDate")
    List<Object[]> findDistinctItemsForReview(@Param("thresholdDate") LocalDateTime thresholdDate);

    // Đếm số lượng tiến độ học tập đã đến hạn hoặc quá hạn ôn tập của 1 user
    long countByUserIdAndNextReviewDateLessThanEqual(UUID userId, LocalDateTime dateTime);

}
