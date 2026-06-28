package com.longdq.adaptengbackend.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class DailyTestRecordDto {
    private Long recordId;
    private String status;
    private List<ToeicPassageResponseDto> testContent;
    private Map<Long, String> savedAnswers;
}