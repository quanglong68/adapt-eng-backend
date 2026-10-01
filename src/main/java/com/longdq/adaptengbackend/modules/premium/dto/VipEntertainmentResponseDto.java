package com.longdq.adaptengbackend.modules.premium.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VipEntertainmentResponseDto {
    private Long id;
    private String contentJson;
    private Boolean isCompleted;
    private LocalDate entertainmentDate;
    /** Trạng thái rõ nghĩa cho FE (giữ isCompleted/contentJson cũ để tương thích). */
    private VipEntertainmentStatus status;
    /** Lý do khi status = EMPTY. */
    private VipEntertainmentEmptyReason emptyReason;
}