package com.longdq.adaptengbackend.scheduler;

import com.longdq.adaptengbackend.enums.Level;
import com.longdq.adaptengbackend.service.WritingDataSyncService;
import com.longdq.adaptengbackend.util.RetryExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WritingQuestionGeneratorJob {

    private final WritingDataSyncService writingDataSyncService;
    private static final int MAX_RETRY_TIMES = 4;
    private static final int QUESTIONS_PER_LEVEL = 5; // Số câu hỏi muốn sinh mỗi tháng cho mỗi Level

    // Chạy vào lúc 0h ngày mùng 1 hàng tháng giống hệ thống Reading
    @Scheduled(cron = "0 0 0 1 * ?")
    public void syncMonthlyWritingPart1Questions() {
        log.info("BẮT ĐẦU CRONJOB: Sinh đề TOEIC Writing Part 1 hàng tháng");

        Level[] levels = {Level.A1, Level.A2, Level.B1, Level.B2, Level.C1, Level.C2};

        for (Level level : levels) {
            log.info("Đang xử lý sinh đề cho Level: {}", level.name());

            // Dùng RetryExecutor y hệt bên Reading để bảo vệ chống rớt mạng / Timeout API
            RetryExecutor.executeWithRetry(
                    () -> writingDataSyncService.generateAndSaveWritingPart1Questions(level, QUESTIONS_PER_LEVEL),
                    "WRITING PART 1 (" + level + ")",
                    MAX_RETRY_TIMES,
                    15_000,  // Ngủ 15s nếu lỗi trước khi thử lại
                    5_000
            );

            log.info("Hoàn tất Level {}. Chờ 10s làm mát hệ thống trước khi qua Level tiếp theo.", level);
            sleepQuietly(10_000);
        }

        log.info("🎉 KẾT THÚC CRONJOB: Sinh đề TOEIC Writing Part 1 thành công!");
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}