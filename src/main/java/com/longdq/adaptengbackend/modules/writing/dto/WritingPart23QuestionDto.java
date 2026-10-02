package com.longdq.adaptengbackend.modules.writing.dto;

import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 1 item trong session Writing hỗn hợp 3×P1 + 1×P2 + 1×P3.
 * P1 dùng imageUrl/givenWords/requiredGrammar (giữ nguyên cơ chế cũ).
 * P2/P3 dùng các trường email, essay, directions + requiredConstraints (SM-2 due gắn lúc ráp session).
 */
@Data
public class WritingPart23QuestionDto {
    private Long questionId;
    private String toeicPart; // WRITING_PART_1 | WRITING_PART_2 | WRITING_PART_3

    // Part 1 (giữ nguyên)
    private String imageUrl;
    private String givenWords;
    private UUID knowledgeItemId;
    private String requiredGrammar;

    // Part 2 (Email)
    private String emailFrom;
    private String emailTo;
    private String emailDate;
    private String emailSubject;
    private String emailBody;

    // Dùng chung P2/P3
    private String directions;

    // Part 3 (Essay)
    private String essayType;
    private String essayQuestion;

    // Ràng buộc SM-2 bắt buộc dùng trong bài (P2 tối đa 2, P3 tối đa 5, P1/Test = rỗng)
    private List<String> requiredConstraints;
}
