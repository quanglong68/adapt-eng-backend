package com.longdq.adaptengbackend.entity;

import com.longdq.adaptengbackend.enums.LearningTrack;
import com.longdq.adaptengbackend.enums.ToeicPart;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_learning_progress")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserLearningProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "knowledge_item_id")
    private KnowledgeItem knowledgeItem;

    @Column(name = "target_word")
    private String targetWord;

    @Column(name = "repetition_count")
    private Integer repetitionCount = 0;

    @Column(name = "ease_factor")
    private Double easeFactor = 2.5;

    @Column(name = "interval_days")
    private Integer intervalDays = 1;

    @Column(name = "next_review_date")
    private LocalDateTime nextReviewDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "learning_track")
    private LearningTrack learningTrack;

    @Enumerated(EnumType.STRING)
    @Column(name = "toeic_part")
    private ToeicPart toeicPart;
}