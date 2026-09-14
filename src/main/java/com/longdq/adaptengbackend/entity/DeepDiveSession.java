package com.longdq.adaptengbackend.entity;

import com.longdq.adaptengbackend.enums.DeepDiveStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "deep_dive_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeepDiveSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;


    @Column(name = "knowledge_item_id", nullable = false)
    private UUID knowledgeItemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeepDiveStatus status;

    @Column(name = "questions_json", columnDefinition = "TEXT")
    private String questionsJson;

    @Column(name = "user_answers_json", columnDefinition = "TEXT")
    private String userAnswersJson;

    @Column(name = "score")
    private Integer score;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "target_word")
    private String targetWord;
}