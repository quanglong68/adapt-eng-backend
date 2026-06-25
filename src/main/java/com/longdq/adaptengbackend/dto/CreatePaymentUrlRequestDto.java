package com.longdq.adaptengbackend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentUrlRequestDto {

    @NotNull(message = "packageId is required")
    private Long packageId;
}
