package com.longdq.adaptengbackend.modules.toeic.dto;

import com.longdq.adaptengbackend.common.enums.QuestionType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToeicQuestionResponseDto {
    private Long questionId;
    private String content;
    private List<String> options;
    private QuestionType questionType;
}