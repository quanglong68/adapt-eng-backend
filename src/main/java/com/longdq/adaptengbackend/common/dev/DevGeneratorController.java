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
}
