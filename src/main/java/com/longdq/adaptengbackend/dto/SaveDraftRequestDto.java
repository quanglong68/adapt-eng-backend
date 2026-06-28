package com.longdq.adaptengbackend.dto;

import lombok.Data;
import java.util.Map;

@Data
public class SaveDraftRequestDto {
    private Map<Long, String> answers;
}