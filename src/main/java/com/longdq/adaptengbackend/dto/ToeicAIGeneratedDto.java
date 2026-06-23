package com.longdq.adaptengbackend.dto;

import com.longdq.adaptengbackend.enums.KnowledgeType;
import lombok.Data;
import java.util.List;

@Data
public class ToeicAIGeneratedDto {
    private String passageContent;
    private List<ToeicQuestionDto> questions;

    @Data
    public static class ToeicQuestionDto {
        private String content;
        private List<String> options;
        private String correctAnswer;
        private String explanation;
        private KnowledgeType knowledgeType;
        private String knowledgeName;
        private String targetWord;
    }
}