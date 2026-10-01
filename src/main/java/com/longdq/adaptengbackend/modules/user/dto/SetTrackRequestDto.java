package com.longdq.adaptengbackend.modules.user.dto;

import com.longdq.adaptengbackend.common.enums.LearningTrack;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SetTrackRequestDto {
    @NotNull(message = "Lộ trình học không được để trống")
    private LearningTrack learningTrack;
}