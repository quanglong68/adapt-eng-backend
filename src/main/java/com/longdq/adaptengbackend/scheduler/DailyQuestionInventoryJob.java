package com.longdq.adaptengbackend.scheduler;

import com.longdq.adaptengbackend.entity.KnowledgeItem;
import com.longdq.adaptengbackend.enums.Level;
import com.longdq.adaptengbackend.enums.Purpose;
import com.longdq.adaptengbackend.enums.ToeicPart;
import com.longdq.adaptengbackend.repository.KnowledgeItemRepository;
import com.longdq.adaptengbackend.repository.QuestionRepository;
import com.longdq.adaptengbackend.repository.UserLearningProgressRepository;
import com.longdq.adaptengbackend.repository.UserRepository;
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
    private final KnowledgeItemRepository knowledgeItemRepository;

    private static final int MAX_RETRY_TIMES = 5;
    private static final int MIN_POOL_SIZE = 30;
    private static final int BATCH_SIZE = 6;
    private static final int THRESHOLD = 30;

    @Scheduled(cron = "0 0 2 * * ?")
    public void checkAndRefillToeicInventory() {
        System.out.println("\n--- 🛠️ BẮT ĐẦU KIỂM KHO ĐẠN TOEIC ÔN TẬP (NGƯỠNG: 30) ---");

        LocalDateTime threeDaysLater = LocalDateTime.now().plusDays(3);
        List<Object[]> distinctItems = progressRepository.findDistinctToeicItemsForReview(threeDaysLater);

        if (distinctItems.isEmpty()) {
            System.out.println("✅ Không có nợ SM-2 sắp hạn. Kho đạn an toàn.");
            return;
        }

        for (Object[] item : distinctItems) {
            UUID kId = (UUID) item[0];
            String targetWord = (String) item[2];
            String partName = item[3] != null ? item[3].toString() : null;
            String levelName = item[4] != null ? item[4].toString() : "B1";

            if (partName == null || levelName == null) continue;

            ToeicPart toeicPart = ToeicPart.valueOf(partName);
            Level level = Level.valueOf(levelName);

            // Đếm số đạn hiện có
            long currentStock = questionRepository.countGlobalQuestions(kId, targetWord);

            if (currentStock < THRESHOLD) {
                String taskName = "Bơm đạn [" + toeicPart.name() + "] từ: '" + targetWord + "'";
                System.out.println("\n⚠️ Kho chứa: " + currentStock + "/" + THRESHOLD + " -> " + taskName);

                KnowledgeItem kItem = (kId != null) ? knowledgeItemRepository.findById(kId).orElse(null) : null;

                executeWithRetry(() -> {
                    if (toeicPart == ToeicPart.PART_5) {
                        dataSyncService.generateAndSaveToeicPart5(level, kItem, targetWord, Purpose.PRACTICE);
                    } else {
                        // AI đẻ 1 đoạn văn. Vài đêm sau sẽ đủ 30
                        dataSyncService.generateAndSaveToeicPassage(level, toeicPart, kItem, targetWord, Purpose.PRACTICE);
                    }
                }, taskName);
            }
        }
        System.out.println("\n🎉 TIẾN TRÌNH NẠP KHO ĐÊM ĐÃ HOÀN TẤT! 🎉");
    }

    /**
     * Hàm helper xử lý Retry Cục Bộ giống hệt luồng Monthly Test.
     * Thất bại 1 từ vựng không làm chết toàn bộ tiến trình nạp kho.
     */
    private void executeWithRetry(Runnable task, String taskName) {
        int attempt = 0;
        boolean success = false;

        while (attempt < MAX_RETRY_TIMES && !success) {
            try {
                attempt++;
                System.out.println("⏳ Đang xử lý: " + taskName + " (Lần thử: " + attempt + ")...");
                task.run();
                success = true;

                // Nghỉ 4s tránh Rate Limit của Google API (Giới hạn 15 RPM của bản miễn phí)
                Thread.sleep(4000);
            } catch (Exception e) {
                System.err.println("❌ Lỗi sinh " + taskName + ": " + e.getMessage());
                if (attempt < MAX_RETRY_TIMES) {
                    System.out.println("🔄 Đang thử lại sau 10 giây...");
                    try { Thread.sleep(10000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                } else {
                    System.err.println("🚨 Đã bỏ cuộc sinh " + taskName + " sau " + MAX_RETRY_TIMES + " lần thử.");
                }
            }
        }
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void checkAndRefillQuestionInventory() {
        System.out.println("\n--- BẮT ĐẦU KIỂM KHO CÂU HỎI ÔN TẬP ---");

        LocalDateTime threeDaysLater = LocalDateTime.now().plusDays(3);
        List<Object[]> distinctItems = progressRepository.findDistinctToeicItemsForReview(threeDaysLater);

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