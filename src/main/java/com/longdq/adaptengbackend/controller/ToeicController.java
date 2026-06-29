package com.longdq.adaptengbackend.controller;

import com.longdq.adaptengbackend.dto.*;
import com.longdq.adaptengbackend.enums.Level;
import com.longdq.adaptengbackend.scheduler.QuestionTestGeneratorJob;
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
    public ResponseEntity<DailyReviewResultResponseDto> submitDailyToeicPractice(@RequestBody DailyReviewSubmissionRequestDto request) {
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
    public ResponseEntity<TestSubmissionResponseDto> submitLevelUpTest(@RequestBody TestSubmissionRequestDto request) {
        return ResponseEntity.ok(toeicTestService.submitLevelUpTest(request));
    }

    private final QuestionTestGeneratorJob testGeneratorJob;
    @PostMapping("/admin/force-generate/{level}")
    public ResponseEntity<String> forceGenerateTest(@PathVariable Level level) {
        // Mở một Thread riêng (Chạy ngầm) để không bị block Time-out Postman
        new Thread(() -> {
            try {
                System.out.println("🚀 [DEV] Bắt đầu Force Generate đề thi TOEIC cho level: " + level);
                testGeneratorJob.generateToeicTestForOneLevel(level);
                System.out.println("✅ [DEV] Đã Generate thành công 50 câu cho level: " + level);
            } catch (Exception e) {
                System.err.println("❌ [DEV] Lỗi khi Generate: " + e.getMessage());
            }
        }).start();

        return ResponseEntity.ok("Đã nhận lệnh! Server đang gọi AI để đẻ đề thi " + level + " (khoảng 3-5 phút). Vui lòng check màn hình Console (Terminal) của Backend để xem tiến độ.");
    }
}