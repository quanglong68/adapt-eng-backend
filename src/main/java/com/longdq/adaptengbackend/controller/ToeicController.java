package com.longdq.adaptengbackend.controller;

import com.longdq.adaptengbackend.dto.*;
import com.longdq.adaptengbackend.enums.Level;
import com.longdq.adaptengbackend.service.ToeicPracticeService;
import com.longdq.adaptengbackend.service.ToeicTestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/v1/toeic")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ToeicController {

    private final ToeicTestService toeicTestService;
    private final ToeicPracticeService toeicPracticeService;

    // ======================================================
    // 1. CÁC ENDPOINT PHỤC VỤ LUỒNG MONTHLY TEST (50 CÂU)
    // ======================================================
    @GetMapping("/test/generate/{level}")
    public ResponseEntity<List<ToeicPassageResponseDto>> getToeicTest(@PathVariable Level level) {
        return ResponseEntity.ok(toeicTestService.getToeicTestQuestions(level));
    }

    @PostMapping("/test/submit")
    public ResponseEntity<TestSubmissionResponseDto> submitToeicTest(@RequestBody TestSubmissionRequestDto request) {
        return ResponseEntity.ok(toeicTestService.submitToeicTest(request));
    }

    // ======================================================
    // 2. CÁC ENDPOINT PHỤC VỤ LUỒNG DAILY PRACTICE (SM-2 MAY ĐO)
    // ======================================================
    @GetMapping("/practice/daily")
    public ResponseEntity<List<ToeicPassageResponseDto>> getDailyToeicPractice() {
        return ResponseEntity.ok(toeicPracticeService.generateDailyToeicTest());
    }

    @PostMapping("/practice/submit")
    public ResponseEntity<DailyReviewResultResponseDto> submitDailyToeicPractice(@RequestBody DailyReviewSubmissionRequestDto request) {
        return ResponseEntity.ok(toeicPracticeService.submitDailyToeicReview(request));
    }
}