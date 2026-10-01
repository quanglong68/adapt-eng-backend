package com.longdq.adaptengbackend.common.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserAnswerDto {
    @NotNull(message = "questionId không được để trống")
    private Long questionId;

    // Cho phép chuỗi rỗng: câu bỏ trống được tính 0 điểm (không phải lỗi).
    // Chỉ chặn khi key thiếu hẳn (null) để phát hiện payload sai format.
    @NotNull(message = "Đáp án không được để trống")
    private String selectedAnswer;
}
