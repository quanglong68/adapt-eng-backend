package com.longdq.adaptengbackend.modules.user.dto;

import com.longdq.adaptengbackend.common.enums.Level;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SetLevelRequestDto {
    @NotNull(message = "Trình độ không được để trống")
    private Level selectedLevel;
}