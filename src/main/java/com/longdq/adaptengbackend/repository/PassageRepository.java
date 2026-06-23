package com.longdq.adaptengbackend.repository;

import com.longdq.adaptengbackend.entity.Passage;
import com.longdq.adaptengbackend.enums.LearningTrack;
import com.longdq.adaptengbackend.enums.ToeicPart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PassageRepository extends JpaRepository<Passage, Long> {

    List<Passage> findByLearningTrackAndToeicPart(LearningTrack learningTrack, ToeicPart toeicPart);

    // 1. LẤY BÀI RANDOM MỚI TOANH CHƯA TỪNG ĐỌC
    @Query(value = "SELECT p.* FROM passages p " +
            "WHERE p.toeic_part = :toeicPart AND p.skill = 'READING' " +
            "AND p.id NOT IN :pickedPassageIds " +
            "AND NOT EXISTS (" +
            "   SELECT 1 FROM questions q JOIN user_question_history uqh ON q.id = uqh.question_id " +
            "   WHERE q.passage_id = p.id AND uqh.user_id = :userId" +
            ") " +
            "ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<Passage> findUnansweredPassagesToFill(@Param("toeicPart") String toeicPart,
                                               @Param("userId") UUID userId,
                                               @Param("pickedPassageIds") List<Long> pickedPassageIds,
                                               @Param("limit") int limit);

    // 2. PHƯƠNG ÁN DỰ PHÒNG: LẤY BÀI ĐỌC CŨ NHẤT ĐÃ LÀM (KHI AI SẬP & DB HẾT BÀI MỚI)
    // Đã sửa lại SQL để PostgreSQL không bị lỗi DISTINCT kết hợp ORDER BY
    @Query(value = "SELECT p.* FROM passages p " +
            "WHERE p.toeic_part = :toeicPart " +
            "AND p.id NOT IN :pickedPassageIds " +
            "AND p.id IN (SELECT q.passage_id FROM questions q JOIN user_question_history uqh ON q.id = uqh.question_id WHERE uqh.user_id = :userId) " +
            "ORDER BY (" +
            "    SELECT MIN(uqh2.answered_at) " +
            "    FROM questions q2 " +
            "    JOIN user_question_history uqh2 ON q2.id = uqh2.question_id " +
            "    WHERE q2.passage_id = p.id AND uqh2.user_id = :userId" +
            ") ASC " +
            "LIMIT :limit", nativeQuery = true)
    List<Passage> findOldestAnsweredPassagesToFill(@Param("toeicPart") String toeicPart,
                                                   @Param("userId") UUID userId,
                                                   @Param("pickedPassageIds") List<Long> pickedPassageIds,
                                                   @Param("limit") int limit);

    // 3. Bốc ngẫu nhiên các đoạn văn dành riêng cho bài thi hàng tháng
    @Query(value = "SELECT p.* FROM passages p " +
            "WHERE p.id IN (" +
            "   SELECT DISTINCT q.passage_id FROM questions q " +
            "   WHERE q.learning_track = 'TOEIC' " +
            "   AND q.toeic_part = :toeicPart " +
            "   AND q.level = :level " +
            "   AND q.purpose = 'TEST' " +
            "   AND q.passage_id IS NOT NULL" +
            ") " +
            "ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<Passage> findRandomToeicPassages(@Param("toeicPart") String toeicPart,
                                          @Param("level") String level,
                                          @Param("limit") int limit);
}