package com.longdq.adaptengbackend.controller;

import com.longdq.adaptengbackend.dto.DailyReviewSubmissionRequestDto;
import com.longdq.adaptengbackend.dto.SaveDraftRequestDto;
import com.longdq.adaptengbackend.dto.TestSubmissionRequestDto;
import com.longdq.adaptengbackend.dto.TestSubmissionResponseDto;
import com.longdq.adaptengbackend.dto.WritingPracticeHistoryDto;
import com.longdq.adaptengbackend.dto.WritingPracticeResultResponseDto;
import com.longdq.adaptengbackend.dto.WritingPracticeSessionDto;
import com.longdq.adaptengbackend.dto.WritingTestResponseDto;
import com.longdq.adaptengbackend.entity.User;
import com.longdq.adaptengbackend.enums.Level;
import com.longdq.adaptengbackend.service.WritingDataSyncService;
import com.longdq.adaptengbackend.service.WritingPracticeService;
import com.longdq.adaptengbackend.service.WritingTestService;
import com.longdq.adaptengbackend.util.SecurityUtils;
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
    private final WritingDataSyncService writingDataSyncService;
    private final WritingPracticeService writingPracticeService;

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
            @RequestBody TestSubmissionRequestDto request) {
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
            @RequestBody DailyReviewSubmissionRequestDto request) {
        return ResponseEntity.ok(writingPracticeService.submitDailyPractice(request));
    }

    // 6. API lịch sử luyện tập Writing
    @GetMapping("/practice/history")
    public ResponseEntity<List<WritingPracticeHistoryDto>> getWritingPracticeHistory() {
        return ResponseEntity.ok(writingPracticeService.getPracticeHistory());
    }

    // 7. API dành cho Admin / Dev để ép hệ thống đẻ đề ngay lập tức
    @PostMapping("/admin/force-generate/{level}")
    public ResponseEntity<String> forceGenerateTest(@PathVariable Level level) {
        // Mở một Thread riêng (Chạy ngầm) để không bị block Time-out Postman (Giống hệt file ToeicController của bạn)
        new Thread(() -> {
            try {
                System.out.println("🚀 [DEV] Bắt đầu Force Generate đề thi Writing cho level: " + level);

                // Đẻ thử 5 câu cho Level được chọn
                writingDataSyncService.generateAndSaveWritingPart1Questions(level, 5);

                System.out.println("✅ [DEV] Đã Generate thành công 5 câu Writing cho level: " + level);
            } catch (Exception e) {
                System.err.println("❌ [DEV] Lỗi khi Generate: " + e.getMessage());
            }
        }).start();

        return ResponseEntity.ok("Đã nhận lệnh! Server đang gọi AI (Gemini + FLUX) để đẻ đề thi Writing " + level + ". Quá trình này mất khoảng 30s-60s (vì phải vẽ ảnh và up Cloud). Vui lòng check Terminal.");
    }


}