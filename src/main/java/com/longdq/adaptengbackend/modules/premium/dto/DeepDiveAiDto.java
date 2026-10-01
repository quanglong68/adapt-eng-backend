package com.longdq.adaptengbackend.modules.premium.dto;

import lombok.Data;
import java.util.List;

public class DeepDiveAiDto {

    // 1. DTO chung cho 1 Câu hỏi (Dùng cho cả 3 loại đề)
    @Data
    public static class QuestionDto {
        private String questionType;
        private String content;
        private List<String> options;
        private String correctAnswer;
        private String explanation;
        private String knowledgeName;
        private String knowledgeType;
        private String targetWord;
    }

    // 2. DTO để hứng nhánh Đọc hiểu (Reading)
    @Data
    public static class ReadingResponseDto {
        private List<PassageDto> passages;
    }

    // 3. DTO chứa Bài văn và mảng Câu hỏi (Dành riêng cho Reading)
    @Data
    public static class PassageDto {
        private String passageContent;
        private List<QuestionDto> questions;
    }
}