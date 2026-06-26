package com.longdq.adaptengbackend.entity;

import com.longdq.adaptengbackend.enums.VipSavedWordStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "vip_saved_words")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VipSavedWord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String word;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VipSavedWordStatus status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}