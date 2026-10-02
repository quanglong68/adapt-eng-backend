package com.longdq.adaptengbackend.modules.premium.dto;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;
import java.util.Map;
import java.util.List;

public class DeepDiveDto {

    @Data
    @Builder
    public static class RecommendationResponse {
        private UUID knowledgeItemId;
        private String targetWord;
        private String knowledgeName;
        private double easeFactor;
        private String difficultyLevel;
        // Part phát sinh điểm yếu (WRITING_PART_1/2/3, PART_5/6/7_...) để FE tách theo tab kỹ năng
        private String toeicPart;
        // 🚀 ĐÃ THÊM: 2 trường này để báo cho Frontend biết trạng thái hiện tại của đề
        private UUID activeSessionId;
        private String activeSessionStatus;
    }

    @Data
    @Builder
    public static class InitResponse {
        private UUID sessionId;
        private String status;
        private String message;
    }

    @Data
    public static class InitRequest {
        private UUID knowledgeItemId;
        private String targetWord;
    }

    @Data
    public static class SubmitRequest {
        private Map<Long, String> answers;
    }

    @Data
    @Builder
    public static class SubmitResponse {
        private int score;
        private int total;
        private double scorePercent;
        private String message;
        private List<ReviewDto> reviewList;
    }

    @Data
    @Builder
    public static class ReviewDto {
        private Long questionId;
        private boolean isCorrect;
        private String userSelectedAnswer;
        private String correctAnswer;
        private String explanation;
        private String knowledgeName;
    }

    @Data
    @Builder
    public static class PassageResponse {
        private Long passageId;
        private String toeicPart;
        private String passageContent;
        private List<QuestionResponse> questions;
    }

    @Data
    @Builder
    public static class QuestionResponse {
        private Long questionId;
        private String content;
        private List<String> options;
    }
}