package com.longdq.adaptengbackend.dto;

import lombok.Data;

@Data
public class WritingAiGradingResponseDto {
    private Long questionId;
    private Integer score; // Điểm 0 - 3
    private String feedback; // Nhận xét + Câu sửa lỗi
    private Weakness weakness;

    @Data
    public static class Weakness {
        private String knowledgeType; // Enum string
        private String knowledgeName; // Tên tiếng Việt
    }
}