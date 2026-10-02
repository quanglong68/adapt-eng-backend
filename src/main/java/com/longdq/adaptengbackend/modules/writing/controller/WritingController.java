package com.longdq.adaptengbackend.modules.writing.controller;

import com.longdq.adaptengbackend.common.dto.DailyReviewSubmissionRequestDto;
import com.longdq.adaptengbackend.common.dto.SaveDraftRequestDto;
import com.longdq.adaptengbackend.common.dto.TestSubmissionRequestDto;
import com.longdq.adaptengbackend.common.dto.TestSubmissionResponseDto;
import com.longdq.adaptengbackend.modules.writing.dto.WritingPracticeHistoryDto;
import com.longdq.adaptengbackend.modules.writing.dto.WritingPracticeResultResponseDto;
import com.longdq.adaptengbackend.modules.writing.dto.WritingPracticeSessionDto;
import com.longdq.adaptengbackend.modules.writing.dto.WritingPart23ResultDto;
import com.longdq.adaptengbackend.modules.writing.dto.WritingPart23SessionDto;
import com.longdq.adaptengbackend.modules.writing.service.WritingPart23PracticeService;
import com.longdq.adaptengbackend.modules.writing.service.WritingPart23TestService;
import com.longdq.adaptengbackend.modules.writing.dto.WritingTestResponseDto;
import com.longdq.adaptengbackend.modules.user.entity.User;
import com.longdq.adaptengbackend.common.enums.Level;
import com.longdq.adaptengbackend.modules.writing.service.WritingPracticeService;
import com.longdq.adaptengbackend.modules.writing.service.WritingTestService;
import com.longdq.adaptengbackend.common.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/writing")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class WritingController {

    private final WritingTestService writingTestService;
    private final WritingPracticeService writingPracticeService;
    private final WritingPart23TestService writingPart23TestService;
    private final WritingPart23PracticeService writingPart23PracticeService;

    // 1. API lấy đề thi đầu vào theo Level
    @PostMapping("/placement-test/start/{level}")
    public ResponseEntity<WritingTestResponseDto> startPlacementTest(@PathVariable Level level) {
        User user = SecurityUtils.getCurrentUser();
        WritingTestResponseDto response = writingTestService.generatePlacementTest(user.getId(), level);
        return ResponseEntity.ok(response);
    }

    // 2. API nộp bài Test đầu vào
    @PostMapping("/placement-test/submit")
    public ResponseEntity<TestSubmissionResponseDto> submitPlacementTest(
            @Valid @RequestBody TestSubmissionRequestDto request) {
        User user = SecurityUtils.getCurrentUser();
        TestSubmissionResponseDto response = writingTestService.submitPlacementTest(request, user);
        return ResponseEntity.ok(response);
    }

    // ================= LUYỆN TẬP HÀNG NGÀY =================

    // 3. API lấy đề luyện tập hàng ngày (tự tạo mới nếu chưa có, hoặc trả về đề đang làm dở)
    @GetMapping("/practice/daily")
    public ResponseEntity<WritingPracticeSessionDto> getDailyWritingPractice() {
        return ResponseEntity.ok(writingPracticeService.getOrCreateDailyPractice());
    }

    // 4. API lưu nháp câu trả lời (Auto-Save)
    @PutMapping("/practice/save-draft")
    public ResponseEntity<Void> saveWritingPracticeDraft(@RequestBody SaveDraftRequestDto request) {
        writingPracticeService.saveDraft(request);
        return ResponseEntity.ok().build();
    }

    // 5. API nộp bài luyện tập -> AI chấm điểm 0-3 mỗi câu
    @PostMapping("/practice/submit")
    public ResponseEntity<WritingPracticeResultResponseDto> submitWritingPractice(
            @Valid @RequestBody DailyReviewSubmissionRequestDto request) {
        return ResponseEntity.ok(writingPracticeService.submitDailyPractice(request));
    }

    // 6. API lịch sử luyện tập Writing
    @GetMapping("/practice/history")
    public ResponseEntity<List<WritingPracticeHistoryDto>> getWritingPracticeHistory() {
        return ResponseEntity.ok(writingPracticeService.getPracticeHistory());
    }

    // ================= SESSION HỖN HỢP 3×P1 + P2 + P3 =================
    // Tên path test chứa "placement-test" để PlacementTestInterceptor thả cửa như luồng cũ.

    // 7. Tạo/lấy đề Test hỗn hợp theo level
    @PostMapping("/placement-test/combined/start/{level}")
    public ResponseEntity<WritingPart23SessionDto> startCombinedTest(@PathVariable Level level) {
        User user = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(writingPart23TestService.generateCombinedTest(user.getId(), level));
    }

    // 8. Nộp bài Test hỗn hợp (không ràng buộc)
    @PostMapping("/placement-test/combined/submit")
    public ResponseEntity<WritingPart23ResultDto> submitCombinedTest(
            @Valid @RequestBody TestSubmissionRequestDto request) {
        User user = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(writingPart23TestService.submitCombinedTest(request, user));
    }

    // 9. Lấy đề Daily hỗn hợp (kèm required_constraints P2/P3)
    @GetMapping("/combined/practice/daily")
    public ResponseEntity<WritingPart23SessionDto> getCombinedDailyPractice() {
        return ResponseEntity.ok(writingPart23PracticeService.getOrCreateDailyPractice());
    }

    // 10. Lưu nháp Daily hỗn hợp
    @PutMapping("/combined/practice/save-draft")
    public ResponseEntity<Void> saveCombinedDraft(@RequestBody SaveDraftRequestDto request) {
        writingPart23PracticeService.saveDraft(request);
        return ResponseEntity.ok().build();
    }

    // 11. Nộp bài Daily hỗn hợp (chấm phạt constraints ở backend)
    @PostMapping("/combined/practice/submit")
    public ResponseEntity<WritingPart23ResultDto> submitCombinedPractice(
            @Valid @RequestBody DailyReviewSubmissionRequestDto request) {
        return ResponseEntity.ok(writingPart23PracticeService.submitDailyPractice(request));
    }

    // 12. Lịch sử Daily hỗn hợp
    @GetMapping("/combined/practice/history")
    public ResponseEntity<List<WritingPracticeHistoryDto>> getCombinedPracticeHistory() {
        return ResponseEntity.ok(writingPart23PracticeService.getPracticeHistory());
    }

}