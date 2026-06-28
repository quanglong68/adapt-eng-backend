package com.longdq.adaptengbackend.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class DailyPracticeSessionDto {
    private Long recordId;
    private String status;
    private List<ToeicPassageResponseDto> testContent; // Đề bài (Đã giấu đáp án)
    private Map<Long, String> savedAnswers; // Các đáp án user đang tick dở
}