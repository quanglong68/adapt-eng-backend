package com.longdq.adaptengbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class QuestionReviewDto {
    private Long questionId;
    private String userSelectedAnswer;
    private String correctAnswer;
    private boolean isCorrect;
    private String explanation;
    private String knowledgeName;
}
