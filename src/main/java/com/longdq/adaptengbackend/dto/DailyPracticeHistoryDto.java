package com.longdq.adaptengbackend.dto;

import lombok.Data;

@Data
public class DailyPracticeHistoryDto {
    private Long recordId;
    private String status;
    private String testDate;
    private Integer score;
    private Integer totalQuestions;
    private String reviewJson; // Chứa nguyên bộ Giải thích + Đáp án đúng
    private String questionsJson;
}