package com.longdq.adaptengbackend.modules.writing.scheduler;

import com.longdq.adaptengbackend.common.enums.Level;
import com.longdq.adaptengbackend.common.util.RetryExecutor;
import com.longdq.adaptengbackend.modules.writing.service.WritingDataSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * JOB 2:00 AM SINH CÂU HỎI LẺ P2/P3 (quy tắc cứng §6.3).
 * Không sinh "đề", chỉ sinh "câu hỏi lẻ" nạp vào kho writing_questions.
 * Mỗi đêm duyệt tất cả Level, mỗi level 1 câu Part 2 + 1 câu Part 3.
 * Part 1 đã có cơ chế riêng nên job này bỏ qua. Không quét SM-2, không kiểm ngưỡng.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WritingPart23NightlyGeneratorJob {

    private final WritingDataSyncService writingDataSyncService;

    private static final int MAX_RETRY_TIMES = 4;

    @Scheduled(cron = "0 0 2 * * ?")
    public void generateNightlyPart23Questions() {
        log.info("BẮT ĐẦU JOB 2AM: Sinh câu hỏi lẻ Writing Part 2 + Part 3 cho mọi level");
        Level[] levels = {Level.A1, Level.A2, Level.B1, Level.B2, Level.C1, Level.C2};

        for (Level level : levels) {
            RetryExecutor.executeWithRetry(
                    () -> writingDataSyncService.generateAndSaveWritingPart2Question(level),
                    "WRITING PART 2 (" + level + ")",
                    MAX_RETRY_TIMES,
                    15_000,
                    5_000);
            sleepQuietly(5_000);

            RetryExecutor.executeWithRetry(
                    () -> writingDataSyncService.generateAndSaveWritingPart3Question(level),
                    "WRITING PART 3 (" + level + ")",
                    MAX_RETRY_TIMES,
                    15_000,
                    5_000);
            sleepQuietly(5_000);
        }
        log.info("KẾT THÚC JOB 2AM: Đã bổ sung Part 2 + Part 3 cho {} levels", levels.length);
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
