package com.longdq.adaptengbackend.modules.premium.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VipActionResponseDto {
    private boolean success;
    private String message;
    private Boolean locked;
    private Integer currentCount;
    private Integer maxCount;
}