package com.longdq.adaptengbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user_question_history")
public class UserQuestionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "is_correct", nullable = false)
    private boolean isCorrect;

    @Column(name = "answered_at", nullable = false)
    private LocalDateTime answeredAt;
}