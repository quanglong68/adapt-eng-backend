package com.longdq.adaptengbackend.scheduler;

import com.longdq.adaptengbackend.entity.KnowledgeItem;
import com.longdq.adaptengbackend.enums.Level;
import com.longdq.adaptengbackend.enums.ToeicPart;
import com.longdq.adaptengbackend.repository.KnowledgeItemRepository;
import com.longdq.adaptengbackend.repository.UserLearningProgressRepository;
import com.longdq.adaptengbackend.repository.WritingQuestionRepository;
import com.longdq.adaptengbackend.service.WritingDataSyncService;
import com.longdq.adaptengbackend.util.RetryExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * JOB QUÉT NGỮ PHÁP WRITING PART 1 HÀNG NGÀY.
 *
 * Mỗi đêm (2h sáng), quét toàn bộ KnowledgeItem Writing đang đến hạn ôn tập theo SM-2.
 * Với mỗi ngữ pháp có tồn kho câu hỏi thấp hơn ngưỡng, nhờ Gemini sinh thêm câu hỏi
 * XOÁY ĐÚNG vào ngữ pháp đó, để lần sau user ôn tập luôn có đề đúng điểm yếu.
 *
 * Mirror cơ chế DailyQuestionInventoryJob (Reading) nhưng chạy trên bảng writing_questions
 * và ép KnowledgeItem mục tiêu thay vì bốc part/targetWord.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WritingDailyInventoryJob {

    private final UserLearningProgressRepository progressRepository;
    private final WritingQuestionRepository writingQuestionRepository;
    private final KnowledgeItemRepository knowledgeItemRepository;
    private final WritingDataSyncService writingDataSyncService;

    private static final int MAX_RETRY_TIMES = 4;
    private static final int MIN_STOCK_PER_GRAMMAR = 10; // Dưới ngưỡng này thì bơm thêm
    private static final int QUESTIONS_PER_GRAMMAR = 5;  // Số câu sinh thêm mỗi lần
    private static final int LOOK_AHEAD_DAYS = 3;        // Quét trước các mục sắp đến hạn 3 ngày

    @Scheduled(cron = "0 0 2 * * ?")
    public void checkAndRefillWritingInventory() {
        log.info("BẮT ĐẦU JOB: Quét ngữ pháp Writing Part 1 đến hạn để bơm đề (ngưỡng tồn kho {})",
                MIN_STOCK_PER_GRAMMAR);

        LocalDateTime lookAheadDate = LocalDateTime.now().plusDays(LOOK_AHEAD_DAYS);
        List<Object[]> dueItems = progressRepository.findDistinctWritingGrammarItemsForReview(
                lookAheadDate, ToeicPart.WRITING_PART_1);

        if (dueItems.isEmpty()) {
            log.info("Không có ngữ pháp Writing nào đến hạn ôn. Kho đề đang khỏe, bỏ qua.");
            return;
        }

        int refilled = 0;
        for (Object[] item : dueItems) {
            UUID knowledgeItemId = (UUID) item[0];
            String knowledgeName = (String) item[1];

            if (knowledgeItemId == null) {
                continue;
            }

            Level level = item[2] != null ? Level.valueOf(item[2].toString()) : Level.B1;
            long currentStock = writingQuestionRepository.countByKnowledgeItemId(knowledgeItemId);

            if (currentStock >= MIN_STOCK_PER_GRAMMAR) {
                continue;
            }

            KnowledgeItem knowledgeItem = knowledgeItemRepository.findById(knowledgeItemId).orElse(null);
            if (knowledgeItem == null) {
                log.warn("Không tìm thấy KnowledgeItem {} để bơm đề Writing, bỏ qua.", knowledgeItemId);
                continue;
            }

            String taskName = "Bơm đề Writing ngữ pháp '" + (knowledgeName != null ? knowledgeName : knowledgeItemId)
                    + "' (level " + level + ")";
            log.warn("Tồn kho {}/{} -> {}", currentStock, MIN_STOCK_PER_GRAMMAR, taskName);

            RetryExecutor.executeWithRetry(
                    () -> writingDataSyncService.generateAndSaveWritingPart1Questions(
                            level, knowledgeItem, QUESTIONS_PER_GRAMMAR),
                    taskName,
                    MAX_RETRY_TIMES,
                    15_000,
                    5_000
            );
            refilled++;
        }

        log.info("KẾT THÚC JOB: Đã xử lý {} ngữ pháp Writing cần bơm đề.", refilled);
    }
}
