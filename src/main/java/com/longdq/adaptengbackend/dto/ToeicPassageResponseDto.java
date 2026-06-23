package com.longdq.adaptengbackend.dto;

import com.longdq.adaptengbackend.enums.ToeicPart;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToeicPassageResponseDto {
    private Long passageId;        // Sẽ là null đối với Part 5
    private String passageContent; // Sẽ là null đối với Part 5 (Chỉ xuất hiện 1 lần duy nhất cho mỗi đoạn văn Part 6/7)
    private ToeicPart toeicPart;
    private List<ToeicQuestionResponseDto> questions;
}