package com.longdq.adaptengbackend.controller;

import com.longdq.adaptengbackend.dto.QuestionResponseDto;
import com.longdq.adaptengbackend.dto.SetLevelRequestDto;
import com.longdq.adaptengbackend.dto.TestSubmissionRequestDto;
import com.longdq.adaptengbackend.dto.TestSubmissionResponseDto;
import com.longdq.adaptengbackend.enums.Level;

import com.longdq.adaptengbackend.service.TestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/v1/test")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TestController {
    private final TestService testService;
//    @GetMapping("/generate/{level}")
//    public ResponseEntity<List<QuestionResponseDto>> getTest(@PathVariable Level level) {
//        return ResponseEntity.ok(testService.getTestQuestions(level));
//    }
//
//    // 2. Nộp bài và chấm điểm
//    @PostMapping("/submit")
//    public ResponseEntity<TestSubmissionResponseDto> submitTest(@RequestBody TestSubmissionRequestDto request) {
//        return ResponseEntity.ok(testService.submitTest(request));
//    }

    // 3. Chốt trình độ thực tế
    @PostMapping("/set-level")
    public ResponseEntity<String> setLevel(@RequestBody SetLevelRequestDto request) {
        testService.setUserCurrentLevel(request);
        return ResponseEntity.ok("Đã cập nhật trình độ hiện tại thành: " + request.getSelectedLevel());
    }
}
