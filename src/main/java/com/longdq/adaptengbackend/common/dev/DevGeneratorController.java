package com.longdq.adaptengbackend.common.dev;

import com.longdq.adaptengbackend.common.enums.Level;
import com.longdq.adaptengbackend.modules.toeic.scheduler.QuestionTestGeneratorJob;
import com.longdq.adaptengbackend.modules.writing.service.WritingDataSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

/**
 * Endpoint mồi dữ liệu bằng AI — chỉ tồn tại ở profile {@code dev}.
 * Prod không load bean này nên các path /admin/force-generate sẽ trả 404.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Profile("dev")
@CrossOrigin(origins = "*")
public class DevGeneratorController {

    private final QuestionTestGeneratorJob testGeneratorJob;
    private final WritingDataSyncService writingDataSyncService;

    @PostMapping("/api/v1/toeic/admin/force-generate/{level}")
    public ResponseEntity<String> forceGenerateToeicTest(@PathVariable Level level) {
        CompletableFuture.runAsync(() -> {
            try {
                log.info("[DEV] Bắt đầu Force Generate đề thi TOEIC cho level: {}", level);
                testGeneratorJob.generateToeicTestForOneLevel(level);
                log.info("[DEV] Đã Generate thành công 50 câu cho level: {}", level);
            } catch (Exception e) {
                log.error("[DEV] Lỗi khi Generate TOEIC: {}", e.getMessage(), e);
            }
        });

        return ResponseEntity.ok("Đã nhận lệnh! Server đang gọi AI để đẻ đề thi " + level + " (khoảng 3-5 phút). Vui lòng check màn hình Console (Terminal) của Backend để xem tiến độ.");
    }

    @PostMapping("/api/v1/writing/admin/force-generate/{level}")
    public ResponseEntity<String> forceGenerateWritingTest(@PathVariable Level level) {
        CompletableFuture.runAsync(() -> {
            try {
                log.info("[DEV] Bắt đầu Force Generate đề thi Writing cho level: {}", level);
                writingDataSyncService.generateAndSaveWritingPart1Questions(level, 5);
                log.info("[DEV] Đã Generate thành công 5 câu Writing cho level: {}", level);
            } catch (Exception e) {
                log.error("[DEV] Lỗi khi Generate Writing: {}", e.getMessage(), e);
            }
        });

        return ResponseEntity.ok("Đã nhận lệnh! Server đang gọi AI (Gemini + FLUX) để đẻ đề thi Writing " + level + ". Quá trình này mất khoảng 30s-60s (vì phải vẽ ảnh và up Cloud). Vui lòng check Terminal.");
    }

    /**
     * Mồi câu hỏi lẻ Writing Part 2 + Part 3 cho 1 level (phục vụ test session hỗn hợp).
     * Chỉ sinh đề text (Gemini), không vẽ ảnh nên nhanh (~10-20s/câu).
     */
    @PostMapping("/api/v1/writing/admin/force-generate/part23/{level}")
    public ResponseEntity<String> forceGenerateWritingPart23(
            @PathVariable Level level,
            @RequestParam(defaultValue = "3") int count) {
        int safeCount = Math.max(1, Math.min(10, count));
        CompletableFuture.runAsync(() -> {
            try {
                log.info("[DEV] Bắt đầu Force Generate {} cặp P2/P3 cho level {}", safeCount, level);
                for (int i = 0; i < safeCount; i++) {
                    writingDataSyncService.generateAndSaveWritingPart2Question(level);
                    writingDataSyncService.generateAndSaveWritingPart3Question(level);
                }
                log.info("[DEV] Đã Generate xong {} cặp P2/P3 cho level {}", safeCount, level);
            } catch (Exception e) {
                log.error("[DEV] Lỗi khi Generate Writing P2/P3: {}", e.getMessage(), e);
            }
        });

        return ResponseEntity.ok("Đã nhận lệnh! Server đang gọi Gemini để sinh " + safeCount
                + " câu Part 2 + " + safeCount + " câu Part 3 cho level " + level + ". Vui lòng check Terminal.");
    }

    /**
     * Mồi câu hỏi lẻ Writing Part 2 + Part 3 cho TẤT CẢ level (A1..C2).
     * Chạy 1 lần là đủ data test mọi level (mỗi level count cặp P2/P3).
     */
    @PostMapping("/api/v1/writing/admin/force-generate/part23/all")
    public ResponseEntity<String> forceGenerateWritingPart23All(
            @RequestParam(defaultValue = "3") int count) {
        int safeCount = Math.max(1, Math.min(10, count));
        CompletableFuture.runAsync(() -> {
            try {
                log.info("[DEV] Bắt đầu Force Generate {} cặp P2/P3 cho TẤT CẢ level", safeCount);
                for (Level level : Level.values()) {
                    for (int i = 0; i < safeCount; i++) {
                        writingDataSyncService.generateAndSaveWritingPart2Question(level);
                        writingDataSyncService.generateAndSaveWritingPart3Question(level);
                    }
                    log.info("[DEV] Xong level {}", level);
                }
                log.info("[DEV] Đã Generate xong P2/P3 cho tất cả level");
            } catch (Exception e) {
                log.error("[DEV] Lỗi khi Generate Writing P2/P3 all levels: {}", e.getMessage(), e);
            }
        });

        return ResponseEntity.ok("Đã nhận lệnh! Server đang sinh " + safeCount
                + " cặp P2/P3 cho mỗi level (6 levels). Quá trình mất vài phút, vui lòng check Terminal.");
    }
}
