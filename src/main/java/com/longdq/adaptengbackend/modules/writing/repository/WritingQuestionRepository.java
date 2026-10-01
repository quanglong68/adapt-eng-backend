package com.longdq.adaptengbackend.modules.writing.repository;

import com.longdq.adaptengbackend.modules.writing.entity.WritingQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.longdq.adaptengbackend.common.enums.Level;
import com.longdq.adaptengbackend.modules.spacedrepetition.entity.KnowledgeItem;
import com.longdq.adaptengbackend.modules.toeic.repository.QuestionRepository;

@Repository
public interface WritingQuestionRepository extends JpaRepository<WritingQuestion, Long> {

    // Bốc ngẫu nhiên N câu hỏi theo Part và Level (Chuẩn cơ chế Reading)
    @Query(value = "SELECT * FROM writing_questions WHERE toeic_part = :part AND level = :level ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<WritingQuestion> findRandomByPartAndLevel(@Param("part") String part, @Param("level") String level, @Param("limit") int limit);

    // Đếm tồn kho câu hỏi Writing gắn với 1 KnowledgeItem (ngữ pháp), dùng cho job bơm đề SM-2
    long countByKnowledgeItemId(UUID knowledgeItemId);

    // ==========================================
    // NHÓM TRUY VẤN BỐC ĐỀ CHO WRITING PRACTICE (SM-2)
    // Mirror QuestionRepository.findNewPart5Question/findLruPart5Question/findUnansweredPart5ToFill
    // nhưng JOIN bảng lịch sử riêng user_writing_question_history để không đụng Reading.
    // ==========================================

    // Lấy câu CHƯA TỪNG LÀM, khớp đúng chủ đề ngữ pháp đang cần luyện (Ưu tiên SM-2)
    @Query(value = "SELECT w.* FROM writing_questions w " +
            "LEFT JOIN user_writing_question_history uwh ON w.id = uwh.question_id AND uwh.user_id = :userId " +
            "WHERE w.toeic_part = 'WRITING_PART_1' " +
            "AND (CAST(:knowledgeItemId AS UUID) IS NULL OR w.knowledge_item_id = CAST(:knowledgeItemId AS UUID)) " +
            "AND w.id NOT IN :pickedQuestionIds " +
            "AND uwh.id IS NULL " + // ÉP LẤY CÂU CHƯA TỪNG LÀM
            "ORDER BY RANDOM() LIMIT 1", nativeQuery = true)
    Optional<WritingQuestion> findNewWritingQuestion(
            @Param("knowledgeItemId") UUID knowledgeItemId,
            @Param("userId") UUID userId,
            @Param("pickedQuestionIds") List<Long> pickedQuestionIds);

    // Lấy câu làm LÂU NHẤT (LRU) khớp chủ đề ngữ pháp (tái sử dụng câu cũ khi đã hết câu mới)
    @Query(value = "SELECT w.* FROM writing_questions w " +
            "JOIN user_writing_question_history uwh ON w.id = uwh.question_id AND uwh.user_id = :userId " +
            "WHERE w.toeic_part = 'WRITING_PART_1' " +
            "AND (CAST(:knowledgeItemId AS UUID) IS NULL OR w.knowledge_item_id = CAST(:knowledgeItemId AS UUID)) " +
            "AND w.id NOT IN :pickedQuestionIds " +
            "ORDER BY uwh.answered_at ASC LIMIT 1", nativeQuery = true)
    Optional<WritingQuestion> findLruWritingQuestion(
            @Param("knowledgeItemId") UUID knowledgeItemId,
            @Param("userId") UUID userId,
            @Param("pickedQuestionIds") List<Long> pickedQuestionIds);

    // Bù câu cho đủ số lượng khi SM-2 không đáp ứng được (bốc ngẫu nhiên trong đúng level)
    @Query(value = "SELECT w.* FROM writing_questions w " +
            "LEFT JOIN user_writing_question_history uwh ON w.id = uwh.question_id AND uwh.user_id = :userId " +
            "WHERE w.toeic_part = 'WRITING_PART_1' AND w.level = :level " +
            "AND w.id NOT IN :pickedQuestionIds " +
            "AND uwh.id IS NULL " +
            "ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<WritingQuestion> findUnansweredWritingToFill(
            @Param("level") String level,
            @Param("userId") UUID userId,
            @Param("pickedQuestionIds") List<Long> pickedQuestionIds,
            @Param("limit") int limit);
}