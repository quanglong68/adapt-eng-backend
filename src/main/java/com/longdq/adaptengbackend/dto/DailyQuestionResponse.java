package com.longdq.adaptengbackend.dto;

import com.longdq.adaptengbackend.enums.QuestionType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DailyQuestionResponse {
    @NotNull(message = "Id câu hỏi không được trống!")
    Long questionId;
    @NotNull(message = "questionType câu hỏi không được trống!")
    QuestionType questionType;
    @NotNull(message = "content câu hỏi không được trống!")
    String content;
    @NotNull(message = "options câu hỏi không được trống!")
    List<String> options;
    @NotNull(message = "progressId câu hỏi không được trống!")
    Long progressId;
}
