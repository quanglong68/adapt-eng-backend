package com.longdq.adaptengbackend.common.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DailyReviewSubmissionRequestDto {
    @NotNull(message = "Danh sách câu trả lời không được để trống")
    @Size(min = 1, message = "Phải trả lời ít nhất 1 câu")
    @Valid
    private List<UserAnswerDto> answers;
}
