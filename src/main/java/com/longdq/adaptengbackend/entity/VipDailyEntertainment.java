package com.longdq.adaptengbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "vip_daily_entertainment")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VipDailyEntertainment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "content_json", columnDefinition = "TEXT")
    private String contentJson;

    @Column(name = "is_completed")
    private Boolean isCompleted = false;

    @Column(name = "entertainment_date")
    private LocalDate entertainmentDate;

    @Column(name = "created_at")
    private LocalDate createdAt;
}