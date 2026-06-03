package com.longdq.adaptengbackend.dto;
import com.longdq.adaptengbackend.enums.Level;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TestSubmissionResponseDto {
    private int totalQuestions;
    private int correctAnswers;
    private double scorePercentage;
    private boolean passedThreshold;
    private Level testedLevel;
    private Level recommendedLevel;
    private String systemMessage;
    private List<QuestionReviewDto> reviewList;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class QuestionReviewDto {
        private Long questionId;
        private String userSelectedAnswer;
        private String correctAnswer;
        private boolean isCorrect;
        private String explanation;
        private String knowledgeName;
    }
}