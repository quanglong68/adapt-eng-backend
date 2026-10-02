package com.longdq.adaptengbackend.modules.writing.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Kết quả AI thô cho 1 câu P2/P3 (parse từ Gemini, chưa trừ phạt).
 * Backend tự trừ phạt dựa trên constraintResults + wordCount.
 */
@Data
public class WritingPart23AiResultDto {
    private Integer score;
    private Integer wordCount;
    private String feedback;
    private Map<String, Boolean> constraintResults;
    private List<String> weaknesses;

    // Alias phòng khi AI trả key snake_case
    @JsonAlias("word_count")
    public void setWord_count(Integer wordCount) {
        this.wordCount = wordCount;
    }

    @JsonAlias("constraint_results")
    public void setConstraint_results(Map<String, Boolean> constraintResults) {
        this.constraintResults = constraintResults;
    }
}
