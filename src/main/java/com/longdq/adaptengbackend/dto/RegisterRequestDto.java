package com.longdq.adaptengbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RegisterRequestDto {
    private String fullName;
    private String email;
    private String password;
}
