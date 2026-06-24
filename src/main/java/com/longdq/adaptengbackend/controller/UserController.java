package com.longdq.adaptengbackend.controller;

import com.longdq.adaptengbackend.dto.SetTrackRequestDto;
import com.longdq.adaptengbackend.entity.User;
import com.longdq.adaptengbackend.repository.UserRepository;
import com.longdq.adaptengbackend.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UserController {

    private final UserRepository userRepository;

    @PostMapping("/set-track")
    public ResponseEntity<String> setLearningTrack(@RequestBody SetTrackRequestDto request) {
        User user = SecurityUtils.getCurrentUser();
        user.setLearningTrack(request.getLearningTrack());
        userRepository.save(user);
        return ResponseEntity.ok("Đã cập nhật lộ trình học thành: " + request.getLearningTrack());
    }
}