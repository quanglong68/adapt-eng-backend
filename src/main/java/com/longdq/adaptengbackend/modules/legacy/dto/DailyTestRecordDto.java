package com.longdq.adaptengbackend.modules.legacy.dto;

import com.longdq.adaptengbackend.modules.toeic.dto.ToeicPassageResponseDto;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class DailyTestRecordDto {
    private Long recordId;
    private String status;
    private List<ToeicPassageResponseDto> testContent;
    private Map<Long, String> savedAnswers;
}
