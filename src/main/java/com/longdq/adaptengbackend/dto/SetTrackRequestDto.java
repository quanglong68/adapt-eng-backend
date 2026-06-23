package com.longdq.adaptengbackend.dto;

import com.longdq.adaptengbackend.enums.LearningTrack;
import lombok.Data;

@Data
public class SetTrackRequestDto {
    private LearningTrack learningTrack;
}