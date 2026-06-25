package com.longdq.adaptengbackend.controller;

import com.longdq.adaptengbackend.dto.UserProfileResponseDto;
import com.longdq.adaptengbackend.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UsersController {

    private final UserProfileService userProfileService;

    @GetMapping("/me")
    public UserProfileResponseDto getCurrentUserProfile() {
        return userProfileService.getCurrentUserProfile();
    }
}
