package com.longdq.adaptengbackend.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class WritingTestResponseDto {
    private Long recordId; // ID của record bài thi để lát nữa user nộp bài gọi đúng ID
    private String status; // IN_PROGRESS
    private String testType; // PLACEMENT_TEST
    private List<WritingQuestionDto> questions; // Danh sách 5 câu hỏi
    private Map<String, String> savedAnswers; // Hỗ trợ UI lấp lại đáp án nếu user đang làm dở rồi F5 lại trang
}