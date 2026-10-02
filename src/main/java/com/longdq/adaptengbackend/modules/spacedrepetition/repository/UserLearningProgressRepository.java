package com.longdq.adaptengbackend.modules.spacedrepetition.repository;

import com.longdq.adaptengbackend.modules.spacedrepetition.entity.UserLearningProgress;
import com.longdq.adaptengbackend.common.enums.ToeicPart;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.longdq.adaptengbackend.common.enums.Level;
import com.longdq.adaptengbackend.modules.spacedrepetition.entity.KnowledgeItem;

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

    // HÀM MỚI DÀNH RIÊNG CHO TIẾN TRÌNH TOEIC
    @Query("SELECT DISTINCT u.knowledgeItem.id, k.knowledgeName, u.targetWord, u.toeicPart, k.level " +
            "FROM UserLearningProgress u " +
            "LEFT JOIN u.knowledgeItem k " +
            "WHERE u.nextReviewDate <= :date AND u.toeicPart IS NOT NULL")
    List<Object[]> findDistinctToeicItemsForReview(@Param("date") LocalDateTime date);

    // Quét các KnowledgeItem Writing đến hạn ôn tập, kèm trình độ Writing hiện tại của user để biết sinh đề level nào.
    // Trả về: [0]=knowledgeItem.id (UUID), [1]=knowledgeName (String), [2]=user.writingCurrentLevel (Level)
    @Query("SELECT DISTINCT u.knowledgeItem.id, k.knowledgeName, u.user.writingCurrentLevel " +
            "FROM UserLearningProgress u " +
            "LEFT JOIN u.knowledgeItem k " +
            "WHERE u.nextReviewDate <= :date " +
            "AND u.toeicPart = :part " +
            "AND u.knowledgeItem IS NOT NULL " +
            "AND u.user.writingCurrentLevel IS NOT NULL")
    List<Object[]> findDistinctWritingGrammarItemsForReview(
            @Param("date") LocalDateTime date,
            @Param("part") ToeicPart part);
    // Đếm số lượng tiến độ học tập đã đến hạn hoặc quá hạn ôn tập của 1 user
    long countByUserIdAndNextReviewDateLessThanEqual(UUID userId, LocalDateTime dateTime);

    // Đếm tiến độ đến hạn của 1 user theo từng Part (dùng tách số liệu Writing P1/P2/P3)
    long countByUserIdAndToeicPartAndNextReviewDateLessThanEqual(
            UUID userId,
            ToeicPart toeicPart,
            LocalDateTime dateTime);

    // Quét chủ điểm Writing P2/P3 đến hạn của 1 user (dùng gắn required_constraints lúc ráp session).
    // P2 bốc tối đa 2, P3 bốc tối đa 5 — giới hạn bằng Pageable ở tầng service.
    List<UserLearningProgress> findByUserIdAndToeicPartAndNextReviewDateLessThanEqualOrderByNextReviewDateAsc(
            UUID userId,
            ToeicPart toeicPart,
            LocalDateTime dateTime);

    // Thêm hàm này vào UserLearningProgressRepository.java

    @org.springframework.data.jpa.repository.Query("SELECT p FROM UserLearningProgress p " +
            "WHERE p.user.id = :userId " +
            "AND p.easeFactor < 2.5 " +
            // Nếu chưa học lần nào (IS NULL) hoặc đã học cách đây quá 48 tiếng
            "AND (p.lastReviewDate IS NULL OR p.lastReviewDate < :thresholdDate) " +
            "ORDER BY p.easeFactor ASC")
    java.util.List<UserLearningProgress> findTopWeaknessesForDeepDive(
            @org.springframework.data.repository.query.Param("userId") java.util.UUID userId,
            @org.springframework.data.repository.query.Param("thresholdDate") java.time.LocalDateTime thresholdDate,
            org.springframework.data.domain.Pageable pageable
    );

    // Biến thể lọc theo nhóm Part cho tab kỹ năng: Writing (IN 3 part Writing),
    // Reading gom phần còn lại kèm record cũ chưa có toeicPart (IS NULL).
    @org.springframework.data.jpa.repository.Query("SELECT p FROM UserLearningProgress p " +
            "WHERE p.user.id = :userId " +
            "AND p.easeFactor < 2.5 " +
            "AND (p.lastReviewDate IS NULL OR p.lastReviewDate < :thresholdDate) " +
            "AND p.toeicPart IN :parts " +
            "ORDER BY p.easeFactor ASC")
    java.util.List<UserLearningProgress> findTopWeaknessesForDeepDiveByParts(
            @org.springframework.data.repository.query.Param("userId") java.util.UUID userId,
            @org.springframework.data.repository.query.Param("thresholdDate") java.time.LocalDateTime thresholdDate,
            @org.springframework.data.repository.query.Param("parts") java.util.Collection<ToeicPart> parts,
            org.springframework.data.domain.Pageable pageable
    );

    @org.springframework.data.jpa.repository.Query("SELECT p FROM UserLearningProgress p " +
            "WHERE p.user.id = :userId " +
            "AND p.easeFactor < 2.5 " +
            "AND (p.lastReviewDate IS NULL OR p.lastReviewDate < :thresholdDate) " +
            "AND (p.toeicPart NOT IN :parts OR p.toeicPart IS NULL) " +
            "ORDER BY p.easeFactor ASC")
    java.util.List<UserLearningProgress> findTopWeaknessesForDeepDiveExcludingParts(
            @org.springframework.data.repository.query.Param("userId") java.util.UUID userId,
            @org.springframework.data.repository.query.Param("thresholdDate") java.time.LocalDateTime thresholdDate,
            @org.springframework.data.repository.query.Param("parts") java.util.Collection<ToeicPart> parts,
            org.springframework.data.domain.Pageable pageable
    );


}
