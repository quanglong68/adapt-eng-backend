package com.longdq.adaptengbackend.scheduler;

import com.longdq.adaptengbackend.enums.Level;
import com.longdq.adaptengbackend.enums.Purpose;
import com.longdq.adaptengbackend.enums.ToeicPart;
import com.longdq.adaptengbackend.service.DataSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QuestionTestGeneratorJob {
    private final DataSyncService dataSyncService;
    private static final int MAX_RETRY_TIMES = 6;
    public void generateToeicTestForOneLevel(Level level) {
        System.out.println("\n🚀 BẮT ĐẦU SINH ĐỀ TOEIC TEST (50 CÂU) CHO TRÌNH ĐỘ: " + level.name());

        // 1. Sinh 15 câu Part 5
        executeWithRetry(() -> dataSyncService.generateAndSaveToeicPart5(level, null, null, Purpose.TEST),
                "PART 5 (" + level + ")");

        // 2. Sinh 2 đoạn Part 6
        for (int i = 1; i <= 2; i++) {
            executeWithRetry(() -> dataSyncService.generateAndSaveToeicPassage(level, ToeicPart.PART_6, null, null, Purpose.TEST),
                    "PART 6 - Đoạn " + i + " (" + level + ")");
        }

        // 3. Sinh 3 đoạn Part 7 Single
        for (int i = 1; i <= 3; i++) {
            executeWithRetry(() -> dataSyncService.generateAndSaveToeicPassage(level, ToeicPart.PART_7_SINGLE, null, null, Purpose.TEST),
                    "PART 7 SINGLE - Đoạn " + i + " (" + level + ")");
        }

        // 4. Sinh 3 đoạn Part 7 Multiple
        for (int i = 1; i <= 3; i++) {
            executeWithRetry(() -> dataSyncService.generateAndSaveToeicPassage(level, ToeicPart.PART_7_MULTIPLE, null, null, Purpose.TEST),
                    "PART 7 MULTIPLE - Đoạn " + i + " (" + level + ")");
        }

        System.out.println("🎉 HOÀN TẤT ĐỀ THI CHO TRÌNH ĐỘ: " + level.name() + "\n");
    }

    @Scheduled(cron = "0 0 0 1 * ?")
    public void syncMonthlyToeicQuestions() {
        Level[] levels = {Level.A1, Level.A2, Level.B1, Level.B2, Level.C1, Level.C2};

        for (Level level : levels) {
            System.out.println("\n🚀 BẮT ĐẦU SINH ĐỀ TOEIC TEST (50 CÂU) CHO TRÌNH ĐỘ: " + level.name());

            // 1. Sinh 15 câu Part 5
            executeWithRetry(() -> dataSyncService.generateAndSaveToeicPart5(level, null, null, Purpose.TEST),
                    "PART 5 (" + level + ")");

            // 2. Sinh 2 đoạn Part 6
            for (int i = 1; i <= 2; i++) {
                executeWithRetry(() -> dataSyncService.generateAndSaveToeicPassage(level, ToeicPart.PART_6, null, null, Purpose.TEST),
                        "PART 6 - Đoạn " + i + " (" + level + ")");
            }

            // 3. Sinh 3 đoạn Part 7 Single
            for (int i = 1; i <= 3; i++) {
                executeWithRetry(() -> dataSyncService.generateAndSaveToeicPassage(level, ToeicPart.PART_7_SINGLE, null, null, Purpose.TEST),
                        "PART 7 SINGLE - Đoạn " + i + " (" + level + ")");
            }

            // 4. Sinh 3 đoạn Part 7 Multiple
            for (int i = 1; i <= 3; i++) {
                executeWithRetry(() -> dataSyncService.generateAndSaveToeicPassage(level, ToeicPart.PART_7_MULTIPLE, null, null, Purpose.TEST),
                        "PART 7 MULTIPLE - Đoạn " + i + " (" + level + ")");
            }

            System.out.println("🎉 HOÀN TẤT ĐỀ THI CHO TRÌNH ĐỘ: " + level.name() + "\n");
        }
        System.out.println("\n🎉🎉🎉 TIẾN TRÌNH SINH ĐỀ THI ĐÃ KẾT THÚC! 🎉🎉🎉");
    }

    /**
     * Hàm helper xử lý Retry Cục Bộ. Lỗi ở đâu chỉ làm lại đúng chỗ đó.
     */
    private void executeWithRetry(Runnable task, String taskName) {
        int attempt = 0;
        boolean success = false;

        while (attempt < MAX_RETRY_TIMES && !success) {
            try {
                attempt++;
                System.out.println("⏳ Đang sinh " + taskName + " (Lần thử: " + attempt + ")...");
                task.run();
                success = true;
                System.out.println("✅ " + taskName + " lưu thành công!");

                // Nghỉ 4s tránh Rate Limit của Google API
                Thread.sleep(4000);
            } catch (Exception e) {
                System.err.println("❌ Lỗi sinh " + taskName + ": " + e.getMessage());
                if (attempt < MAX_RETRY_TIMES) {
                    System.out.println("🔄 Đang thử lại " + taskName + " sau 10 giây...");
                    try { Thread.sleep(10000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                } else {
                    System.err.println("🚨 Đã bỏ cuộc sinh " + taskName + " sau " + MAX_RETRY_TIMES + " lần thử.");
                }
            }
        }
    }

    //@Scheduled(initialDelay = 1000, fixedDelay = 8 * 60 * 60 * 1000)
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
                    System.err.println("❌ Lỗi API (Có thể do Quota Google). Đang thử lại lần " + attempt + "/" + MAX_RETRY_TIMES);
                    System.err.println("⏳ Hệ thống sẽ ngủ 60 giây để chờ Google reset bộ đếm...");
                    try {
                        Thread.sleep(60000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            if (!isSuccess) {
                // SỬA LẠI CÂU CHỮ CHO KHỚP VỚI BIẾN MAX_RETRY_TIMES
                System.err.println("🚨 Đã thử " + MAX_RETRY_TIMES + " lần cho " + level + " nhưng Google vẫn báo lỗi. Bỏ qua để chạy level tiếp theo.");
            }

            System.out.println("✅ Xong level " + level + ". Đang ngủ 30s để làm mát server Google trước khi qua level mới...\n");
            try {
                Thread.sleep(30000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("\n🎉🎉🎉 TIẾN TRÌNH SINH CÂU HỎI ĐÃ KẾT THÚC! 🎉🎉🎉");
    }
}