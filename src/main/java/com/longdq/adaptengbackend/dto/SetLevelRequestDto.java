package com.longdq.adaptengbackend.dto;

import com.longdq.adaptengbackend.enums.Level;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SetLevelRequestDto {
    private UUID userId;
    private Level selectedLevel;
}