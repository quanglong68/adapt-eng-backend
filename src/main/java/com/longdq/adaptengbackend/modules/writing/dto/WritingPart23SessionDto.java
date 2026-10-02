package com.longdq.adaptengbackend.modules.writing.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Session làm bài hỗn hợp (Test hoặc Daily): 3×P1 + 1×P2 + 1×P3.
 */
@Data
public class WritingPart23SessionDto {
    private Long recordId;
    private String status;
    private String testType;
    private List<WritingPart23QuestionDto> questions;
    private Map<Long, String> savedAnswers;
}
