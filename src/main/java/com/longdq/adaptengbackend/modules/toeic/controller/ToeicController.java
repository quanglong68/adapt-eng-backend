package com.longdq.adaptengbackend.modules.toeic.controller;

import com.longdq.adaptengbackend.common.enums.Level;
import com.longdq.adaptengbackend.modules.toeic.service.ToeicPracticeService;
import com.longdq.adaptengbackend.modules.toeic.service.ToeicTestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.longdq.adaptengbackend.common.dto.DailyReviewSubmissionRequestDto;
import com.longdq.adaptengbackend.common.dto.SaveDraftRequestDto;
import com.longdq.adaptengbackend.common.dto.TestSubmissionRequestDto;
import com.longdq.adaptengbackend.common.dto.TestSubmissionResponseDto;
import com.longdq.adaptengbackend.modules.toeic.dto.DailyPracticeHistoryDto;
import com.longdq.adaptengbackend.modules.toeic.dto.DailyPracticeSessionDto;
import com.longdq.adaptengbackend.modules.toeic.dto.DailyReviewResultResponseDto;
import com.longdq.adaptengbackend.modules.toeic.dto.ToeicPassageResponseDto;

@RestController
@RequestMapping("/api/v1/toeic")
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
    public ResponseEntity<TestSubmissionResponseDto> submitToeicTest(@Valid @RequestBody TestSubmissionRequestDto request) {
        return ResponseEntity.ok(toeicTestService.submitToeicTest(request));
    }

    // ======================================================
    // 2. CÁC ENDPOINT PHỤC VỤ LUỒNG DAILY PRACTICE (SM-2 MAY ĐO)
    // ======================================================
//    @GetMapping("/practice/daily")
//    public ResponseEntity<List<ToeicPassageResponseDto>> getDailyToeicPractice() {
//        return ResponseEntity.ok(toeicPracticeService.generateDailyToeicTest());
//    }
//
//    @PostMapping("/practice/submit")
//    public ResponseEntity<DailyReviewResultResponseDto> submitDailyToeicPractice(@RequestBody DailyReviewSubmissionRequestDto request) {
//        return ResponseEntity.ok(toeicPracticeService.submitDailyToeicReview(request));
//    }


    @PutMapping("/practice/save-draft")
    public ResponseEntity<Void> saveDailyPracticeDraft(@RequestBody SaveDraftRequestDto request) {
        toeicPracticeService.saveDraft(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/practice/submit")
    public ResponseEntity<DailyReviewResultResponseDto> submitDailyToeicPractice(@Valid @RequestBody DailyReviewSubmissionRequestDto request) {
        return ResponseEntity.ok(toeicPracticeService.submitDailyToeicReview(request));
    }

    @GetMapping("/practice/daily")
    public ResponseEntity<DailyPracticeSessionDto> getDailyToeicPractice() {
        return ResponseEntity.ok(toeicPracticeService.getOrCreateDailyPractice());
    }

    @GetMapping("/practice/history")
    public ResponseEntity<List<DailyPracticeHistoryDto>> getPracticeHistory() {
        return ResponseEntity.ok(toeicPracticeService.getPracticeHistory());
    }
    @PostMapping("/test/level-up/submit")
    public ResponseEntity<TestSubmissionResponseDto> submitLevelUpTest(@Valid @RequestBody TestSubmissionRequestDto request) {
        return ResponseEntity.ok(toeicTestService.submitLevelUpTest(request));
    }
}