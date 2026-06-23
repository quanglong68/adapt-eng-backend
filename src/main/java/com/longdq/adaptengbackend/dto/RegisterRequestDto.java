package com.longdq.adaptengbackend.dto;

import com.longdq.adaptengbackend.enums.LearningTrack;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RegisterRequestDto {
    private String fullName;
    private String email;
    private String password;
    private LearningTrack learningTrack;
}
