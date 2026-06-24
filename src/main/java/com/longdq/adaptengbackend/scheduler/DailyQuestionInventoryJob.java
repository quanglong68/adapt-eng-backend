package com.longdq.adaptengbackend.scheduler;

import com.longdq.adaptengbackend.entity.KnowledgeItem;
import com.longdq.adaptengbackend.enums.Level;
import com.longdq.adaptengbackend.enums.Purpose;
import com.longdq.adaptengbackend.enums.ToeicPart;
import com.longdq.adaptengbackend.repository.KnowledgeItemRepository;
import com.longdq.adaptengbackend.repository.QuestionRepository;
import com.longdq.adaptengbackend.repository.UserLearningProgressRepository;
import com.longdq.adaptengbackend.service.DataSyncService;
import com.longdq.adaptengbackend.util.RetryExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
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
        log.info("Starting TOEIC practice inventory check (threshold: {})", THRESHOLD);

        LocalDateTime threeDaysLater = LocalDateTime.now().plusDays(3);
        List<Object[]> distinctItems = progressRepository.findDistinctToeicItemsForReview(threeDaysLater);

        if (distinctItems.isEmpty()) {
            log.info("No upcoming SM-2 debt. Inventory is healthy.");
            return;
        }

        for (Object[] item : distinctItems) {
            UUID kId = (UUID) item[0];
            String targetWord = (String) item[2];
            String partName = item[3] != null ? item[3].toString() : null;
            String levelName = item[4] != null ? item[4].toString() : "B1";

            if (partName == null || levelName == null) {
                continue;
            }

            ToeicPart toeicPart = ToeicPart.valueOf(partName);
            Level level = Level.valueOf(levelName);

            long currentStock = questionRepository.countGlobalQuestions(kId, targetWord);

            if (currentStock >= THRESHOLD) {
                continue;
            }

            String taskName = "Bơm đạn [" + toeicPart.name() + "] từ: '" + targetWord + "'";
            log.warn("Stock {}/{} -> {}", currentStock, THRESHOLD, taskName);

            KnowledgeItem kItem = (kId != null) ? knowledgeItemRepository.findById(kId).orElse(null) : null;

            RetryExecutor.executeWithRetry(() -> {
                if (toeicPart == ToeicPart.PART_5) {
                    dataSyncService.generateAndSaveToeicPart5(level, kItem, targetWord, Purpose.PRACTICE);
                } else {
                    dataSyncService.generateAndSaveToeicPassage(level, toeicPart, kItem, targetWord, Purpose.PRACTICE);
                }
            }, taskName, MAX_RETRY_TIMES, 10_000, 4_000);
        }
        log.info("Nightly TOEIC inventory refill completed");
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void checkAndRefillQuestionInventory() {
        log.info("Starting general practice inventory check");

        LocalDateTime threeDaysLater = LocalDateTime.now().plusDays(3);
        List<Object[]> distinctItems = progressRepository.findDistinctToeicItemsForReview(threeDaysLater);

        List<Object[]> itemsToRefill = new ArrayList<>();
        for (Object[] item : distinctItems) {
            UUID kId = (UUID) item[0];
            String targetWord = (String) item[2];
            if (questionRepository.countGlobalQuestions(kId, targetWord) < MIN_POOL_SIZE) {
                itemsToRefill.add(item);
            }
        }

        if (itemsToRefill.isEmpty()) {
            log.info("Inventory is full. No refill needed.");
            return;
        }

        log.info("Found {} knowledge items below pool size. Starting refill...", itemsToRefill.size());

        for (int i = 0; i < itemsToRefill.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, itemsToRefill.size());
            List<Object[]> batch = itemsToRefill.subList(i, end);
            String prompt = buildBatchPrompt(batch);
            int batchNumber = i / BATCH_SIZE + 1;

            boolean isSuccess = false;
            int attempt = 0;
            while (!isSuccess && attempt < MAX_RETRY_TIMES) {
                try {
                    attempt++;
                    log.info("Sending batch {} ({} items)", batchNumber, batch.size());
                    dataSyncService.generateAndSaveDailyQuestions(prompt);
                    isSuccess = true;
                } catch (Exception e) {
                    log.error("AI batch {} failed (attempt {}): {}", batchNumber, attempt, e.getMessage());
                    sleepQuietly(15_000);
                }
            }

            if (!isSuccess) {
                log.error("Skipping batch {} after {} failed attempts", batchNumber, MAX_RETRY_TIMES);
            }

            if (end < itemsToRefill.size()) {
                log.info("Cooling down 40s before next batch");
                sleepQuietly(40_000);
            }
        }

        log.info("Nightly general inventory refill completed");
    }

    private String buildBatchPrompt(List<Object[]> batch) {
        StringBuilder promptBuilder = new StringBuilder();
        for (Object[] item : batch) {
            UUID kId = (UUID) item[0];
            String kName = (String) item[1];
            String targetWord = (String) item[2];

            promptBuilder.append("- knowledgeId: ").append(kId == null ? "null" : "\"" + kId + "\"")
                    .append(", targetWord: ").append(targetWord == null ? "null" : "\"" + targetWord + "\"")
                    .append(", Yêu cầu: Tạo 5 câu hỏi");

            if (kName != null) {
                promptBuilder.append(" về chủ điểm ngữ pháp '").append(kName).append("'");
            }
            if (targetWord != null) {
                promptBuilder.append(" tập trung kiểm tra từ vựng '").append(targetWord).append("'");
            }

            promptBuilder.append(".\n");
        }
        return promptBuilder.toString();
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
