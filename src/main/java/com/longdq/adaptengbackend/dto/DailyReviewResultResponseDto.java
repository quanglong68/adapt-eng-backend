package com.longdq.adaptengbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DailyReviewResultResponseDto {
    private int totalQuestions;
    private int correctAnswers;
    private List<QuestionReviewDto> reviewList;
}
