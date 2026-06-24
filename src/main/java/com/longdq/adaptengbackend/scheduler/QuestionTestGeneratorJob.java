package com.longdq.adaptengbackend.scheduler;

import com.longdq.adaptengbackend.enums.Level;
import com.longdq.adaptengbackend.enums.Purpose;
import com.longdq.adaptengbackend.enums.ToeicPart;
import com.longdq.adaptengbackend.service.DataSyncService;
import com.longdq.adaptengbackend.util.RetryExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionTestGeneratorJob {

    private final DataSyncService dataSyncService;
    private static final int MAX_RETRY_TIMES = 6;

    public void generateToeicTestForOneLevel(Level level) {
        log.info("Starting TOEIC test generation (50 questions) for level {}", level.name());

        RetryExecutor.executeWithRetry(
                () -> dataSyncService.generateAndSaveToeicPart5(level, null, null, Purpose.TEST),
                "PART 5 (" + level + ")", MAX_RETRY_TIMES, 10_000, 4_000);

        for (int i = 1; i <= 2; i++) {
            final int passageIndex = i;
            RetryExecutor.executeWithRetry(
                    () -> dataSyncService.generateAndSaveToeicPassage(level, ToeicPart.PART_6, null, null, Purpose.TEST),
                    "PART 6 - Đoạn " + passageIndex + " (" + level + ")", MAX_RETRY_TIMES, 10_000, 4_000);
        }

        for (int i = 1; i <= 3; i++) {
            final int passageIndex = i;
            RetryExecutor.executeWithRetry(
                    () -> dataSyncService.generateAndSaveToeicPassage(level, ToeicPart.PART_7_SINGLE, null, null, Purpose.TEST),
                    "PART 7 SINGLE - Đoạn " + passageIndex + " (" + level + ")", MAX_RETRY_TIMES, 10_000, 4_000);
        }

        for (int i = 1; i <= 3; i++) {
            final int passageIndex = i;
            RetryExecutor.executeWithRetry(
                    () -> dataSyncService.generateAndSaveToeicPassage(level, ToeicPart.PART_7_MULTIPLE, null, null, Purpose.TEST),
                    "PART 7 MULTIPLE - Đoạn " + passageIndex + " (" + level + ")", MAX_RETRY_TIMES, 10_000, 4_000);
        }

        log.info("Completed TOEIC test generation for level {}", level.name());
    }

    @Scheduled(cron = "0 0 0 1 * ?")
    public void syncMonthlyToeicQuestions() {
        Level[] levels = {Level.A1, Level.A2, Level.B1, Level.B2, Level.C1, Level.C2};

        for (Level level : levels) {
            generateToeicTestForOneLevel(level);
        }
        log.info("Monthly TOEIC test generation finished");
    }

    @Scheduled(cron = "0 0 0 1 * ?")
    public void syncMonthlyQuestions() {
        Level[] levels = {Level.A1, Level.A2, Level.B1, Level.B2, Level.C1, Level.C2};

        for (Level level : levels) {
            boolean isSuccess = false;
            int attempt = 0;

            while (!isSuccess && attempt < MAX_RETRY_TIMES) {
                try {
                    attempt++;
                    dataSyncService.generateAndSaveMonthlyTestQuestions(level);
                    isSuccess = true;
                } catch (Exception e) {
                    log.error("API error for level {} (attempt {}/{}): {}",
                            level, attempt, MAX_RETRY_TIMES, e.getMessage());
                    log.info("Waiting 60s for Google quota reset");
                    sleepQuietly(60_000);
                }
            }

            if (!isSuccess) {
                log.error("Abandoning level {} after {} failed attempts", level, MAX_RETRY_TIMES);
            }

            log.info("Completed level {}. Cooling down 30s before next level", level);
            sleepQuietly(30_000);
        }

        log.info("Monthly general test question generation finished");
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
