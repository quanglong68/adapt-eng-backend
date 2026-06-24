package com.longdq.adaptengbackend.util;

import com.longdq.adaptengbackend.dto.QuestionReviewDto;
import com.longdq.adaptengbackend.entity.Question;

public final class QuestionReviewBuilder {

    private QuestionReviewBuilder() {
    }

    public static QuestionReviewDto build(
            Question question, String selectedAnswer, boolean isCorrect, String knowledgeName) {
        QuestionReviewDto review = new QuestionReviewDto();
        review.setQuestionId(question.getId());
        review.setUserSelectedAnswer(selectedAnswer);
        review.setCorrectAnswer(question.getCorrectAnswer());
        review.setCorrect(isCorrect);
        review.setExplanation(question.getExplanation());
        review.setKnowledgeName(knowledgeName);
        return review;
    }
}
