package com.longdq.adaptengbackend.modules.writing.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * LỊCH SỬ LÀM BÀI RIÊNG CHO WRITING PART 1.
 *
 * LÝ DO KHÔNG DÙNG CHUNG BẢNG user_question_history:
 * Bảng đó lưu question_id kiểu Long trần, không có khóa ngoại, trong khi câu hỏi Reading
 * (questions) và Writing (writing_questions) nằm ở hai bảng độc lập nhưng ID có thể trùng nhau.
 * Các query phân tích hiện tại đều JOIN vào bảng questions, nên nếu ghi lịch sử Writing vào đó
 * sẽ làm sai số liệu Radar/RecentActivity và làm hỏng logic bốc đề Reading.
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user_writing_question_history")
public class UserWritingQuestionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // Trỏ vào writing_questions.id
    @Column(name = "question_id", nullable = false)
    private Long questionId;

    // Chỉ đạt TRUE khi câu đó đạt trắc điểm tuyệt đối 3/3
    @Column(name = "is_correct", nullable = false)
    private boolean isCorrect;

    @Column(name = "answered_at", nullable = false)
    private LocalDateTime answeredAt;
}
