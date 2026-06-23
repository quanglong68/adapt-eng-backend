package com.longdq.adaptengbackend.dto;


import com.longdq.adaptengbackend.enums.LearningTrack;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponseDto {
    private String token;
    private String userId;
    private String email;
    private String fullName;
    private LearningTrack learningTrack;
}
