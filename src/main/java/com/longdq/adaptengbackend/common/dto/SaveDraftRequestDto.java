package com.longdq.adaptengbackend.common.dto;

import lombok.Data;
import java.util.Map;

@Data
public class SaveDraftRequestDto {
    private Map<Long, String> answers;
}