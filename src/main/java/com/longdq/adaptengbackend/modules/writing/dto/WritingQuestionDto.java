package com.longdq.adaptengbackend.modules.writing.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class WritingQuestionDto {
    private Long questionId;
    private String imageUrl;
    private String givenWords;

    // Đã thay bằng ID để mai mốt trả về tên hiển thị
    private UUID knowledgeItemId;
    private String requiredGrammar;
}