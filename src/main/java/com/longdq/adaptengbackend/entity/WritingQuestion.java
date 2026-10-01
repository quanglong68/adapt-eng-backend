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
    private ToeicPart toeicPart;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "given_words")
    private String givenWords;

    // ĐÃ XÓA requiredGrammar DẠNG CHUỖI. THAY BẰNG LIÊN KẾT TRỰC TIẾP
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "knowledge_item_id")
    private KnowledgeItem knowledgeItem;

    @Enumerated(EnumType.STRING)
    private Level level;
}