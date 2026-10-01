package com.longdq.adaptengbackend.modules.premium.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.longdq.adaptengbackend.common.enums.QuestionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "deep_dive_questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeepDiveQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deep_dive_set_id", nullable = false)
    @JsonIgnore
    private DeepDiveSet deepDiveSet;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false)
    private QuestionType questionType = QuestionType.MULTIPLE_CHOICE;

    // Nếu là bài Đọc hiểu (Reading), nội dung đoạn văn sẽ lưu ở đây
    @Column(name = "passage_content", columnDefinition = "TEXT")
    private String passageContent;

    // Nội dung câu hỏi cụ thể
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    // 4 Đáp án A, B, C, D lưu dưới dạng JSON
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<String> options;

    @Column(name = "correct_answer", nullable = false)
    private String correctAnswer;

    // Lời giải chi tiết (Có xuống dòng \n và đánh dấu ✅ ❌)
    @Column(columnDefinition = "TEXT", nullable = false)
    private String explanation;

    // Phân loại dạng câu hỏi (Ví dụ: [Từ đồng nghĩa], [Tìm lỗi sai]...)
    @Column(name = "knowledge_name")
    private String knowledgeName;
}