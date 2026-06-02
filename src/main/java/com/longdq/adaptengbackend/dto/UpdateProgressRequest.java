package com.longdq.adaptengbackend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;


@Data
public class UpdateProgressRequest {
    @NotNull(message = "Progress ID không được để trống")
    private Long progressId;

    @NotNull(message = "Kết quả isCorrect không được để trống")
    private Boolean isCorrect;
}
