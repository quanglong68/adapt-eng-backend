package com.longdq.adaptengbackend.scheduler;

import com.longdq.adaptengbackend.repository.QuestionRepository;
import com.longdq.adaptengbackend.repository.UserLearningProgressRepository;
import com.longdq.adaptengbackend.service.DataSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DailyQuestionInventoryJob {

    private final UserLearningProgressRepository progressRepository;
    private final QuestionRepository questionRepository;
    private final DataSyncService dataSyncService;

    private static final int MAX_RETRY_TIMES = 5;
    private static final int MIN_POOL_SIZE = 30;
    private static final int BATCH_SIZE = 6;

    @Scheduled(cron = "0 0 2 * * ?")
    public void checkAndRefillQuestionInventory() {
        System.out.println("\n--- BẮT ĐẦU KIỂM KHO CÂU HỎI ÔN TẬP ---");

        LocalDateTime threeDaysLater = LocalDateTime.now().plusDays(3);
        List<Object[]> distinctItems = progressRepository.findDistinctItemsForReview(threeDaysLater);

        // BƯỚC 1: LỌC RA TẤT CẢ NHỮNG ĐỨA THIẾU ĐẠN
        List<Object[]> itemsToRefill = new ArrayList<>();
        for (Object[] item : distinctItems) {
            UUID kId = (UUID) item[0];
            String targetWord = (String) item[2];
            if (questionRepository.countGlobalQuestions(kId, targetWord) < MIN_POOL_SIZE) {
                itemsToRefill.add(item);
            }
        }

        if (itemsToRefill.isEmpty()) {
            System.out.println("Kho đạn đầy đủ. Không cần nạp thêm.");
            return;
        }

        System.out.println("Phát hiện " + itemsToRefill.size() + " chủ điểm thiếu đạn. Bắt đầu tiến trình nạp...");

        // BƯỚC 2: CHIA LÔ (BATCHING) ĐỂ GỌI AI & CHỜ NGHỈ
        for (int i = 0; i < itemsToRefill.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, itemsToRefill.size());
            List<Object[]> batch = itemsToRefill.subList(i, end);

            // Nối chuỗi cho lô hiện tại
            StringBuilder promptBuilder = new StringBuilder();
            for (Object[] item : batch) {
                UUID kId = (UUID) item[0];
                String kName = (String) item[1]; // Lấy thêm Tên chủ điểm từ DB
                String targetWord = (String) item[2];

                promptBuilder.append("- knowledgeId: ").append(kId == null ? "null" : "\"" + kId.toString() + "\"")
                        .append(", targetWord: ").append(targetWord == null ? "null" : "\"" + targetWord + "\"")
                        .append(", Yêu cầu: Tạo 5 câu hỏi");

                // CHỈ ĐIỂM RÕ RÀNG CHO AI:
                if (kName != null) promptBuilder.append(" về chủ điểm ngữ pháp '").append(kName).append("'");
                if (targetWord != null) promptBuilder.append(" tập trung kiểm tra từ vựng '").append(targetWord).append("'");

                promptBuilder.append(".\n");
            }
            // Gọi AI với cơ chế Retry
            boolean isSuccess = false;
            int attempt = 0;
            while (!isSuccess && attempt < MAX_RETRY_TIMES) {
                try {
                    attempt++;
                    System.out.println("Đang gửi lô " + (i / BATCH_SIZE + 1) + " (Gồm " + batch.size() + " chủ điểm)...");
                    dataSyncService.generateAndSaveDailyQuestions(promptBuilder.toString());
                    isSuccess = true;
                } catch (Exception e) {
                    System.err.println("Lỗi AI lô " + (i / BATCH_SIZE + 1) + " (Thử lại lần " + attempt + "): " + e.getMessage());
                    try { Thread.sleep(15000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }

            if (!isSuccess) {
                System.err.println("Bỏ qua lô " + (i / BATCH_SIZE + 1) + " vì lỗi quá 5 lần.");
            }

            // TƯ DUY CỦA LONG: Nghỉ xả hơi 40 giây trước khi gọi lô tiếp theo (Né Rate Limit)
            if (end < itemsToRefill.size()) {
                System.out.println("Đang ngủ 40s để làm mát server Google...\n");
                try { Thread.sleep(40000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        }

        System.out.println("\n🎉 TIẾN TRÌNH NẠP KHO ĐÃ HOÀN TẤT TRONG ĐÊM! 🎉");
    }
}