package com.longdq.adaptengbackend.dto;
import com.longdq.adaptengbackend.enums.Level;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TestSubmissionRequestDto {
    private UUID userId; // Tạm dùng để test, sau này lấy từ Security Context
    private Level testedLevel;
    private List<UserAnswerDto> answers;

    @Data
    @AllArgsConstructor
    public static class UserAnswerDto {
        private Long questionId;
        private String selectedAnswer;
    }
}