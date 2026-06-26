package com.longdq.adaptengbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VipPendingWordDto {
    private Long id;
    private String word;
    private LocalDateTime createdAt;
}