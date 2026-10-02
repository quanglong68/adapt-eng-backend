package com.longdq.adaptengbackend.modules.writing.entity;

import com.longdq.adaptengbackend.common.enums.Level;
import com.longdq.adaptengbackend.common.enums.ToeicPart;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.longdq.adaptengbackend.modules.spacedrepetition.entity.KnowledgeItem;

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

    // === MỞ RỘNG CHO WRITING PART 2 (Email) & PART 3 (Essay) ===
    // Part 1 dùng image_url + given_words. P2/P3 dùng các cột dưới, để null cho Part 1.

    @Column(name = "email_from")
    private String emailFrom;

    @Column(name = "email_to")
    private String emailTo;

    @Column(name = "email_date")
    private String emailDate;

    @Column(name = "email_subject")
    private String emailSubject;

    @Column(name = "email_body", columnDefinition = "TEXT")
    private String emailBody;

    // Hướng dẫn làm bài (directions) dùng chung cho P2/P3
    @Column(name = "directions", columnDefinition = "TEXT")
    private String directions;

    // Dạng đề Essay: Agree/Disagree | Preference | Advantages/Disadvantages
    @Column(name = "essay_type")
    private String essayType;

    @Column(name = "essay_question", columnDefinition = "TEXT")
    private String essayQuestion;
}