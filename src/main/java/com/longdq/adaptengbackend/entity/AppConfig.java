package com.longdq.adaptengbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "app_configs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppConfig {

    @Id
    @Column(name = "config_key", nullable = false, length = 50)
    private String configKey; // Ví dụ: "MIN_PRACTICE_SCORE_PERCENT"

    @Column(name = "config_value", nullable = false)
    private String configValue; // Ví dụ: "10"
}