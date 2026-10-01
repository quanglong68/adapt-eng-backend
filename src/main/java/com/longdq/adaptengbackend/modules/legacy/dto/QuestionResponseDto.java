package com.longdq.adaptengbackend.modules.legacy.dto;

import com.longdq.adaptengbackend.common.enums.QuestionType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class QuestionResponseDto {
    private Long questionId;
    private String content ;
    private List<String> options;
    private QuestionType  questionType;
}
