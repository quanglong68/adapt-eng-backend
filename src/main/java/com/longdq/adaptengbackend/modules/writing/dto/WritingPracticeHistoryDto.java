package com.longdq.adaptengbackend.modules.writing.dto;

import lombok.Data;
import com.longdq.adaptengbackend.modules.toeic.dto.DailyPracticeHistoryDto;

/**
 * Một dòng lịch sử luyện tập Writing (mirror DailyPracticeHistoryDto của Reading).
 */
@Data
public class WritingPracticeHistoryDto {
    private Long recordId;
    private String status;
    private String testDate;
    private Integer score;
    private Integer totalQuestions;
    private String reviewJson;
    private String questionsJson;
}
