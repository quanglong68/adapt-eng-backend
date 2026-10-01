package com.longdq.adaptengbackend.modules.premium.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VipEntertainmentStatusDto {
    private long pendingCount;
    private boolean vip;
    private boolean hasIncompleteStory;
    /** Giờ job sinh đề chạy hàng ngày (02:00). */
    private String nextRunAt;
}
