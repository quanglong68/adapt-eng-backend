package com.longdq.adaptengbackend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SaveWordRequestDto {

    @NotBlank(message = "word is required")
    private String word;
}
