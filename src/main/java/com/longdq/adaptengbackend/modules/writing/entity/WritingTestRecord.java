package com.longdq.adaptengbackend.modules.writing.entity;

import com.longdq.adaptengbackend.common.enums.Level;
import com.longdq.adaptengbackend.common.enums.TestRecordStatus;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "writing_test_records")
@Data
public class WritingTestRecord {

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

    // Phân loại bài test: PLACEMENT_TEST (Thi xếp lớp) hoặc DAILY_TEST (Luyện hàng ngày)
    @Column(name = "test_type", nullable = false)
    private String testType;

    // 1. Cột lưu khung đề lúc gen (Chứa link ảnh, given_words, required_grammar... giấu điểm)
    @Column(name = "questions_json", columnDefinition = "TEXT")
    private String questionsJson;

    // 2. Cột lưu câu trả lời user đang gõ dở (Dùng cho tính năng Auto-Save)
    @Column(name = "user_answers_json", columnDefinition = "TEXT")
    private String userAnswersJson;

    // 3. Cột lưu Lịch sử Full (Có điểm 0-3 từng câu, nhận xét của AI, lỗi ngữ pháp)
    @Column(name = "review_json", columnDefinition = "TEXT")
    private String reviewJson;

    private Integer score; // Điểm user đạt được (VD: 10)
    private Integer totalQuestions; // Tổng số câu (VD: 5)

    // Trình độ của ĐỀ THI (VD: Đề mix A2)
    @Enumerated(EnumType.STRING)
    @Column(name = "level")
    private Level level;

    // Trình độ ĐẠT ĐƯỢC sau bài thi này (Dành riêng cho Placement Test để cập nhật vào bảng users)
    @Enumerated(EnumType.STRING)
    @Column(name = "achieved_level")
    private Level achievedLevel;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}