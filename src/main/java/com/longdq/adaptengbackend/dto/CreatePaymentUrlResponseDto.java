package com.longdq.adaptengbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentUrlResponseDto {

    private String vnpayUrl;
    private String transactionCode;
}
