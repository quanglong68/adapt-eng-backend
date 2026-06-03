package com.longdq.adaptengbackend.scheduler;

import com.longdq.adaptengbackend.enums.Level;
import com.longdq.adaptengbackend.service.DataSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QuestionTestGeneratorJob {
    private final DataSyncService dataSyncService;
    private static final int MAX_RETRY_TIMES = 6;
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
                System.err.println("Lỗi mạng hoặc 503 từ Google. Chờ 15s rồi thử lại...");
                try {
                    Thread.sleep(15000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        if (!isSuccess) {
            System.err.println(" Đã thử 3 lần cho " + level + " nhưng Google vẫn báo lỗi. Bỏ qua để chạy level tiếp theo.");
        }

        System.out.println("Đang ngủ 20s để làm mát server Google...\n");
        try {
            Thread.sleep(20000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    System.out.println("\n🎉🎉🎉 TIẾN TRÌNH SINH CÂU HỎI ĐÃ KẾT THÚC! 🎉🎉🎉");
}
}
