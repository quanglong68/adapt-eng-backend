package com.longdq.adaptengbackend.repository;

import com.longdq.adaptengbackend.entity.Question;
import com.longdq.adaptengbackend.enums.Level;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findByIsAnswerFalse();

    List<Question> findByKnowledgeItemIdIn(List<UUID> knowledgeItemIds);

    @Query(value = """
    SELECT *
    FROM questions q
    WHERE q.level = :#{#level.name()}
      AND q.purpose = 'TEST'
    ORDER BY RANDOM()
    LIMIT 30
""", nativeQuery = true)
    List<Question> find30RandomTestQuestionsByLevel(@Param("level") Level level);

    // 1. Đếm TỔNG KHO của toàn hệ thống
    @Query("SELECT COUNT(q) FROM Question q WHERE " +
            "((:knowledgeItemId IS NULL AND q.knowledgeItem IS NULL) OR q.knowledgeItem.id = :knowledgeItemId) " +
            "AND ((:targetWord IS NULL AND q.targetWord IS NULL) OR q.targetWord = :targetWord)")
    long countGlobalQuestions(
            @Param("knowledgeItemId") UUID knowledgeItemId,
            @Param("targetWord") String targetWord
    );

    // 2. Lấy câu hỏi MỚI TOANH
    @Query(value = "SELECT * FROM questions q WHERE " +
            "((:knowledgeItemId IS NULL AND q.knowledge_item_id IS NULL) OR q.knowledge_item_id = :knowledgeItemId) " +
            "AND (:targetWord IS NULL OR q.target_word = :targetWord) " +
            "AND q.id NOT IN (SELECT uqh.question_id FROM user_question_history uqh WHERE uqh.user_id = :userId) " +
            "AND q.id NOT IN :pickedIds " +
            "ORDER BY RANDOM() LIMIT 1", nativeQuery = true)
    Optional<Question> findRandomQuestionForReview(
            @Param("knowledgeItemId") UUID knowledgeItemId,
            @Param("targetWord") String targetWord,
            @Param("userId") UUID userId,
            @Param("pickedIds") List<Long> pickedIds
    );

    // 3. FALLBACK: Thuật toán LRU - Lấy lại câu cũ nhất (Chống dính lịch sử ảo)
    @Query(value = "SELECT q.* FROM questions q " +
            "JOIN ( " +
            "    SELECT question_id, MAX(answered_at) as last_answered " +
            "    FROM user_question_history " +
            "    WHERE user_id = :userId " +
            "    GROUP BY question_id " +
            ") latest_history ON q.id = latest_history.question_id " +
            "WHERE ((:knowledgeItemId IS NULL AND q.knowledge_item_id IS NULL) OR q.knowledge_item_id = :knowledgeItemId) " +
            "AND (:targetWord IS NULL OR q.target_word = :targetWord) " +
            "AND q.id NOT IN :pickedIds " +
            "ORDER BY latest_history.last_answered ASC LIMIT 1", nativeQuery = true)
    Optional<Question> findOldestAnsweredQuestionForReview(
            @Param("knowledgeItemId") UUID knowledgeItemId,
            @Param("targetWord") String targetWord,
            @Param("userId") UUID userId,
            @Param("pickedIds") List<Long> pickedIds
    );

    // 4. Lấp chỗ trống
    @Query(value = "SELECT * FROM questions q WHERE q.level = :level " +
            "AND q.id NOT IN (SELECT uqh.question_id FROM user_question_history uqh WHERE uqh.user_id = :userId) " +
            "AND q.id NOT IN :pickedIds " +
            "ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<Question> findRandomQuestionsToFill(
            @Param("level") String level,
            @Param("userId") UUID userId,
            @Param("pickedIds") List<Long> pickedIds,
            @Param("limit") int limit
    );
}
