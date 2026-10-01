package com.longdq.adaptengbackend.modules.toeic.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import com.longdq.adaptengbackend.common.dto.QuestionReviewDto;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DailyReviewResultResponseDto {
    private int totalQuestions;
    private int correctAnswers;
    private List<QuestionReviewDto> reviewList;
    private boolean isValidEffort;
    private int earnedXp;
}
