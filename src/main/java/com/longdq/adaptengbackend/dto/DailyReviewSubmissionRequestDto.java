package com.longdq.adaptengbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DailyReviewSubmissionRequestDto {

    private List<UserAnswerDto> answers;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UserAnswerDto {
        private Long questionId;
        private String selectedAnswer; // Đổi tên cho đồng bộ với bên Test
    }
}