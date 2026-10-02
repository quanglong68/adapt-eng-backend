package com.longdq.adaptengbackend.modules.writing.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import com.longdq.adaptengbackend.common.dto.QuestionReviewDto;
import com.longdq.adaptengbackend.common.enums.Level;

/**
 * Kết quả nộp session hỗn hợp 5 câu: P1 max 9 + P2 max 4 + P3 max 5 = 18.
 * recommendedLevel/passedThreshold chỉ có ở luồng Test (đánh giá xếp lớp), Daily để null.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class WritingPart23ResultDto {
    private int totalQuestions;
    private int totalScore;
    private int maxScore;
    private int part1Score;
    private int part2Score;
    private int part3Score;
    private List<QuestionReviewDto> reviewList;
    private boolean isValidEffort;
    private int earnedXp;
    private Level recommendedLevel;
    private Boolean passedThreshold;
}
