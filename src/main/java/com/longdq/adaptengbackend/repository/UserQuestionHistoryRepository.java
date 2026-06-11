package com.longdq.adaptengbackend.repository;

import com.longdq.adaptengbackend.entity.UserQuestionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserQuestionHistoryRepository extends JpaRepository<UserQuestionHistory, Long> {
    interface RecentActivityProjection {
        String getLabel();
        int getScore();
        int getTotal();
        java.sql.Date getAnswerDate();
    }

    // Query gộp nhóm lịch sử câu hỏi thành các lượt làm bài theo ngày và theo chủ điểm
    @Query(value = "SELECT ki.knowledge_name AS label, " +
            "COUNT(CASE WHEN uqh.is_correct = true THEN 1 END) AS score, " + // <-- SỬA CHỖ NÀY THÀNH is_correct
            "COUNT(uqh.id) AS total, " +
            "CAST(uqh.answered_at AS DATE) AS answerDate " +
            "FROM user_question_history uqh " +
            "JOIN questions q ON uqh.question_id = q.id " +
            "LEFT JOIN knowledge_items ki ON q.knowledge_item_id = ki.id " +
            "WHERE uqh.user_id = :userId " +
            "GROUP BY ki.knowledge_name, CAST(uqh.answered_at AS DATE) " +
            "ORDER BY answerDate DESC LIMIT 5", nativeQuery = true)
    List<RecentActivityProjection> findRecentActivities(@Param("userId") UUID userId);

    // Lấy danh sách các ngày duy nhất mà user đã làm bài (Dùng để tính chuỗi Streak)
    @Query(value = "SELECT DISTINCT CAST(uqh.answered_at AS DATE) FROM user_question_history uqh " +
            "WHERE uqh.user_id = :userId ORDER BY CAST(uqh.answered_at AS DATE) DESC", nativeQuery = true)
    List<java.sql.Date> findDistinctAnsweredDates(@Param("userId") UUID userId);

    // 1. Interface hứng dữ liệu cho Đồ thị Radar (Nhóm theo Enum lớn)
    interface RadarStatsProjection {
        String getSkillEnum();
        int getTotalQuestions();
        int getCorrectAnswers();
    }

    @Query(value = "SELECT COALESCE(ki.knowledge_type, 'VOCABULARY') as skillEnum, " +
            "COUNT(uqh.id) as totalQuestions, " +
            "SUM(CASE WHEN uqh.is_correct = true THEN 1 ELSE 0 END) as correctAnswers " +
            "FROM user_question_history uqh " +
            "JOIN questions q ON uqh.question_id = q.id " +
            "LEFT JOIN knowledge_items ki ON q.knowledge_item_id = ki.id " +
            "WHERE uqh.user_id = :userId " +
            "GROUP BY COALESCE(ki.knowledge_type, 'VOCABULARY')", nativeQuery = true)
    List<RadarStatsProjection> findRadarStatsByUserId(@Param("userId") UUID userId);

    // 2. Interface hứng dữ liệu cho chi tiết Lỗ hổng/Thông thạo (Nhóm theo Tên chi tiết)
    interface DetailStatsProjection {
        String getId(); // Knowledge ID hoặc Target Word
        String getSkillName();
        int getTotalQuestions();
        int getCorrectAnswers();
        java.sql.Timestamp getLastMistakeAt();
        java.sql.Timestamp getLastReviewAt();
    }

    @Query(value = "SELECT COALESCE(CAST(ki.id AS VARCHAR), q.target_word) as id, " +
            "COALESCE(ki.knowledge_name, CONCAT('Từ vựng: ', q.target_word)) as skillName, " +
            "COUNT(uqh.id) as totalQuestions, " +
            "SUM(CASE WHEN uqh.is_correct = true THEN 1 ELSE 0 END) as correctAnswers, " +
            "MAX(CASE WHEN uqh.is_correct = false THEN uqh.answered_at ELSE NULL END) as lastMistakeAt, " +
            "MAX(uqh.answered_at) as lastReviewAt " +
            "FROM user_question_history uqh " +
            "JOIN questions q ON uqh.question_id = q.id " +
            "LEFT JOIN knowledge_items ki ON q.knowledge_item_id = ki.id " +
            "WHERE uqh.user_id = :userId " +
            "GROUP BY COALESCE(CAST(ki.id AS VARCHAR), q.target_word), " +
            "COALESCE(ki.knowledge_name, CONCAT('Từ vựng: ', q.target_word))", nativeQuery = true)
    List<DetailStatsProjection> findDetailStatsByUserId(@Param("userId") UUID userId);
}