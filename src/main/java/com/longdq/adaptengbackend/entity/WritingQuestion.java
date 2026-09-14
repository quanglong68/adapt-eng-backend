package com.longdq.adaptengbackend.entity;

import com.longdq.adaptengbackend.enums.Level;
import com.longdq.adaptengbackend.enums.ToeicPart;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "writing_questions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WritingQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "toeic_part", nullable = false)
    private ToeicPart toeicPart; // Mặc định là WRITING_PART_1

    @Column(name = "image_prompt", columnDefinition = "TEXT")
    private String imagePrompt; // Lời nhắc AI đã dùng để vẽ ảnh

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl; // Link ảnh thực tế từ Pollinations

    @Column(name = "given_words")
    private String givenWords; // Ví dụ: "who, present"

    @Column(name = "required_grammar")
    private String requiredGrammar; // Ví dụ: "Mệnh đề quan hệ (Relative Clause)" - Có thể null nếu là câu học mới

    @Enumerated(EnumType.STRING)
    private Level level; // Độ khó của bức ảnh/từ vựng (A1-C1)
}