package com.longdq.adaptengbackend.entity;

import com.longdq.adaptengbackend.enums.TestRecordStatus;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "daily_test_records")
@Data
public class DailyTestRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "test_date", nullable = false)
    private LocalDate testDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TestRecordStatus status;

    // 1. Cột lưu khung đề lúc gen (Chỉ chứa câu hỏi và options, giấu đáp án)
    @Column(name = "questions_json", columnDefinition = "TEXT")
    private String questionsJson;

    // 2. Cột lưu đáp án user đang chọn dở (Dùng cho tính năng Auto-Save)
    @Column(name = "user_answers_json", columnDefinition = "TEXT")
    private String userAnswersJson;

    // 3. Cột lưu Lịch sử Full (Có đáp án đúng, giải thích tiếng Việt, tên điểm ngữ pháp)
    @Column(name = "review_json", columnDefinition = "TEXT")
    private String reviewJson;

    private Integer score;
    private Integer totalQuestions;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}