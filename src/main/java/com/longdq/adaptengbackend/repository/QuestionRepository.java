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

    @Query(value = "SELECT q.* FROM questions q " +
            "WHERE q.learning_track = 'TOEIC' " +
            "AND q.toeic_part = :toeicPart " +
            "AND (CAST(:knowledgeItemId AS UUID) IS NULL OR q.knowledge_item_id = CAST(:knowledgeItemId AS UUID)) " +
            "AND (:targetWord IS NULL OR q.target_word = :targetWord) " +
            "AND q.level = :level " +
            "AND q.id NOT IN :pickedQuestionIds " +
            "AND q.passage_id IS NOT NULL " +
            "ORDER BY RANDOM() LIMIT 1", nativeQuery = true)
    Optional<Question> findTargetedToeicPassageQuestion(
            @Param("toeicPart") String toeicPart,
            @Param("knowledgeItemId") UUID knowledgeItemId,
            @Param("targetWord") String targetWord,
            @Param("level") String level,
            @Param("pickedQuestionIds") List<Long> pickedQuestionIds
    );

    // ==========================================
    // 1. NHÓM TRUY VẤN CHO PART 5
    // ==========================================
    @Query(value = "SELECT q.* FROM questions q " +
            "LEFT JOIN user_question_history uqh ON q.id = uqh.question_id AND uqh.user_id = :userId " +
            "WHERE q.learning_track = 'TOEIC' AND q.toeic_part = 'PART_5' " +
            "AND (CAST(:knowledgeItemId AS UUID) IS NULL OR q.knowledge_item_id = CAST(:knowledgeItemId AS UUID)) " +
            "AND (:targetWord IS NULL OR q.target_word = :targetWord) " +
            "AND q.id NOT IN :pickedQuestionIds " +
            "AND uqh.id IS NULL " + // ÉP LẤY CÂU CHƯA TỪNG LÀM
            "ORDER BY RANDOM() LIMIT 1", nativeQuery = true)
    Optional<Question> findNewPart5Question(@Param("knowledgeItemId") UUID knowledgeItemId, @Param("targetWord") String targetWord, @Param("userId") UUID userId, @Param("pickedQuestionIds") List<Long> pickedQuestionIds);

    @Query(value = "SELECT q.* FROM questions q " +
            "JOIN user_question_history uqh ON q.id = uqh.question_id AND uqh.user_id = :userId " +
            "WHERE q.learning_track = 'TOEIC' AND q.toeic_part = 'PART_5' " +
            "AND (CAST(:knowledgeItemId AS UUID) IS NULL OR q.knowledge_item_id = CAST(:knowledgeItemId AS UUID)) " +
            "AND (:targetWord IS NULL OR q.target_word = :targetWord) " +
            "AND q.id NOT IN :pickedQuestionIds " +
            "ORDER BY uqh.answered_at ASC LIMIT 1", nativeQuery = true) // LẤY CÂU LÀM TỪ LÂU NHẤT (LRU)
    Optional<Question> findLruPart5Question(@Param("knowledgeItemId") UUID knowledgeItemId, @Param("targetWord") String targetWord, @Param("userId") UUID userId, @Param("pickedQuestionIds") List<Long> pickedQuestionIds);

    @Query(value = "SELECT q.* FROM questions q " +
            "LEFT JOIN user_question_history uqh ON q.id = uqh.question_id AND uqh.user_id = :userId " +
            "WHERE q.learning_track = 'TOEIC' AND q.toeic_part = 'PART_5' AND q.level = :level " +
            "AND q.id NOT IN :pickedQuestionIds " +
            "AND uqh.id IS NULL " +
            "ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<Question> findUnansweredPart5ToFill(@Param("level") String level, @Param("userId") UUID userId, @Param("pickedQuestionIds") List<Long> pickedQuestionIds, @Param("limit") int limit);


    // ==========================================
    // 2. NHÓM TRUY VẤN CHO ĐOẠN VĂN (PART 6, 7)
    // ==========================================
    @Query(value = "SELECT q.* FROM questions q " +
            "LEFT JOIN user_question_history uqh ON q.id = uqh.question_id AND uqh.user_id = :userId " +
            "WHERE q.learning_track = 'TOEIC' AND q.toeic_part = :toeicPart " +
            "AND (CAST(:knowledgeItemId AS UUID) IS NULL OR q.knowledge_item_id = CAST(:knowledgeItemId AS UUID)) " +
            "AND (:targetWord IS NULL OR q.target_word = :targetWord) " +
            "AND q.level = :level AND q.passage_id IS NOT NULL " +
            "AND q.id NOT IN :pickedQuestionIds " +
            "AND uqh.id IS NULL " +
            "ORDER BY RANDOM() LIMIT 1", nativeQuery = true)
    Optional<Question> findNewTargetedPassageQuestion(@Param("toeicPart") String toeicPart, @Param("knowledgeItemId") UUID knowledgeItemId, @Param("targetWord") String targetWord, @Param("level") String level, @Param("userId") UUID userId, @Param("pickedQuestionIds") List<Long> pickedQuestionIds);

    @Query(value = "SELECT q.* FROM questions q " +
            "JOIN user_question_history uqh ON q.id = uqh.question_id AND uqh.user_id = :userId " +
            "WHERE q.learning_track = 'TOEIC' AND q.toeic_part = :toeicPart " +
            "AND (CAST(:knowledgeItemId AS UUID) IS NULL OR q.knowledge_item_id = CAST(:knowledgeItemId AS UUID)) " +
            "AND (:targetWord IS NULL OR q.target_word = :targetWord) " +
            "AND q.level = :level AND q.passage_id IS NOT NULL " +
            "AND q.id NOT IN :pickedQuestionIds " +
            "ORDER BY uqh.answered_at ASC LIMIT 1", nativeQuery = true)
    Optional<Question> findLruTargetedPassageQuestion(@Param("toeicPart") String toeicPart, @Param("knowledgeItemId") UUID knowledgeItemId, @Param("targetWord") String targetWord, @Param("level") String level, @Param("userId") UUID userId, @Param("pickedQuestionIds") List<Long> pickedQuestionIds);
    @Query(value = "SELECT * FROM questions " +
            "WHERE learning_track = 'TOEIC' " +
            "AND toeic_part = :toeicPart " +
            "AND level = :level " +
            "AND purpose = 'TEST' " +
            "ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<Question> findRandomToeicQuestions(@Param("toeicPart") String toeicPart,
                                            @Param("level") String level,
                                            @Param("limit") int limit);
}
