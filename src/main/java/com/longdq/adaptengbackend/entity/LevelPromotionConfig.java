package com.longdq.adaptengbackend.entity;

import com.longdq.adaptengbackend.enums.Level;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "level_promotion_configs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LevelPromotionConfig {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "target_level", nullable = false)
    private Level targetLevel;

    @Column(name = "required_total_xp", nullable = false)
    private Integer requiredTotalXp;

    @Column(name = "required_7day_accuracy", nullable = false)
    private Integer required7DayAccuracy;

    @Column(name = "passing_score_percent", nullable = false)
    private Integer passingScorePercent;

    @Column(name = "cooldown_days", nullable = false)
    private Integer cooldownDays;
}