package com.longdq.adaptengbackend.dto;

import com.longdq.adaptengbackend.enums.Level;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponseDto {

    private String email;
    private String fullName;
    private int totalXp;
    private Level currentLevel;
    private boolean isPremium;
    private String currentPackageName;
    private LocalDateTime premiumEndDate;
}
