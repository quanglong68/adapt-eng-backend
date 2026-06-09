package com.longdq.adaptengbackend.dto;

import com.longdq.adaptengbackend.enums.KnowledgeType;
import com.longdq.adaptengbackend.enums.QuestionType;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class AIGeneratedQuestionDto {
    private UUID knowledgeId;
    private String targetWord;
    private QuestionType questionType;
    private String content;
    private List<String> options;
    private String correctAnswer;
    private String explanation;
    private String knowledgeName;
    private KnowledgeType knowledgeType;
}
