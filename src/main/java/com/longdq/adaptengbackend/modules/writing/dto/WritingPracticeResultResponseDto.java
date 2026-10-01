package com.longdq.adaptengbackend.modules.writing.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import com.longdq.adaptengbackend.common.dto.QuestionReviewDto;

/**
 * Kết quả sau khi nộp bài luyện tập Writing.
 * Khác với Reading (đúng/sai), Writing chấm thang 0-3 mỗi câu nên có thêm totalScore/maxScore.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class WritingPracticeResultResponseDto {
    private int totalQuestions;
    private int totalScore;
    private int maxScore;
    private List<QuestionReviewDto> reviewList;
    private boolean isValidEffort;
    private int earnedXp;
}
