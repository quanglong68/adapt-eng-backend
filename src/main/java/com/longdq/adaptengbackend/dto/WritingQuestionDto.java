package com.longdq.adaptengbackend.dto;

import lombok.Data;

@Data
public class WritingQuestionDto {
    private Long questionId;
    private String imageUrl; // Cho FE hiển thị ảnh
    private String givenWords; // Ví dụ: "walk, talk"
    private String requiredGrammar; // Ví dụ: "Mệnh đề quan hệ" (Hiện đỏ lên nhắc nhở user)
}