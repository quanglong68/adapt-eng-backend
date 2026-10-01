package com.longdq.adaptengbackend.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Gói dữ liệu cho phiên luyện tập Writing hàng ngày.
 * Mirror với DailyPracticeSessionDto của Reading.
 */
@Data
public class WritingPracticeSessionDto {
    private Long recordId;
    private String status;
    private List<WritingQuestionDto> questions;
    private Map<Long, String> savedAnswers;
}
