package com.longdq.adaptengbackend.modules.premium.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_deep_dive_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDeepDiveHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "deep_dive_set_id", nullable = false)
    private UUID deepDiveSetId;

    // Tỉ lệ làm đúng của user đối với bộ đề này (0-100%)
    @Column(name = "score_percent")
    private Double scorePercent;

    @CreationTimestamp
    @Column(name = "completed_at", updatable = false)
    private LocalDateTime completedAt;
}