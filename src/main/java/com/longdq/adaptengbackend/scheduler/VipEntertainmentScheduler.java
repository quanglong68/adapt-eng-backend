package com.longdq.adaptengbackend.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.longdq.adaptengbackend.entity.VipDailyEntertainment;
import com.longdq.adaptengbackend.entity.VipSavedWord;
import com.longdq.adaptengbackend.enums.SubscriptionStatus;
import com.longdq.adaptengbackend.enums.VipSavedWordStatus;
import com.longdq.adaptengbackend.repository.UserSubscriptionRepository;
import com.longdq.adaptengbackend.repository.VipDailyEntertainmentRepository;
import com.longdq.adaptengbackend.repository.VipSavedWordRepository;
import com.longdq.adaptengbackend.service.AIService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VipEntertainmentScheduler {

    private final VipSavedWordRepository vipSavedWordRepository;
    private final VipDailyEntertainmentRepository vipDailyEntertainmentRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final AIService aiService;
    private final ObjectMapper objectMapper;

    /**
     * Chạy lúc 2h sáng hàng ngày
     * 1. Dọn rác VipDailyEntertainment đã completed
     * 2. Lấy danh sách userId có từ PENDING, kiểm tra VIP active, gọi AIService, lưu kết quả
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void processDailyVipEntertainment() {
        log.info("=== BẮT ĐẦU VIP ENTERTAINMENT SCHEDULER (2AM) ===");

        // 1. Dọn rác: Xóa các VipDailyEntertainment đã hoàn thành
        cleanupCompletedEntertainments();

        // 2. Lấy danh sách userId duy nhất có từ PENDING (tối ưu O(1) query thay vì O(n) load all users)
        List<UUID> userIdsWithPendingWords = vipSavedWordRepository
                .findDistinctUserIdsByStatus(VipSavedWordStatus.PENDING);

        if (userIdsWithPendingWords.isEmpty()) {
            log.info("Không có user nào có từ PENDING để xử lý.");
            return;
        }

        log.info("Tìm thấy {} user có từ PENDING.", userIdsWithPendingWords.size());

        // 3. Với mỗi userId, kiểm tra VIP active rồi xử lý
        for (UUID userId : userIdsWithPendingWords) {
            try {
                // Kiểm tra user còn VIP active hay không (1 query nhanh)
                boolean isVipActive = userSubscriptionRepository
                        .existsByUserIdAndStatusAndEndDateGreaterThan(
                                userId,
                                SubscriptionStatus.ACTIVE,
                                LocalDateTime.now()
                        );

                if (!isVipActive) {
                    log.info("User {} không còn VIP active, bỏ qua.", userId);
                    continue;
                }

                processUserEntertainment(userId);
            } catch (Exception e) {
                log.error("Lỗi xử lý VIP entertainment cho userId {}: {}", userId, e.getMessage(), e);
            }
        }

        log.info("=== KẾT THÚC VIP ENTERTAINMENT SCHEDULER ===");
    }

    private void cleanupCompletedEntertainments() {
        List<VipDailyEntertainment> completed = vipDailyEntertainmentRepository.findByIsCompleted(true);
        if (!completed.isEmpty()) {
            vipDailyEntertainmentRepository.deleteAll(completed);
            log.info("Đã xóa {} VipDailyEntertainment đã hoàn thành.", completed.size());
        }
    }

    private void processUserEntertainment(UUID userId) {
        // Lấy tối đa 10 từ PENDING
        List<VipSavedWord> pendingWords = vipSavedWordRepository
                .findByUserIdAndStatusOrderByCreatedAtAsc(userId, VipSavedWordStatus.PENDING);

        if (pendingWords.isEmpty()) {
            return;
        }

        // Lấy tối đa 10 từ
        List<VipSavedWord> wordsToProcess = pendingWords.size() > 10
                ? pendingWords.subList(0, 10)
                : pendingWords;

        List<String> wordList = wordsToProcess.stream()
                .map(VipSavedWord::getWord)
                .collect(Collectors.toList());

        // Gọi AIService
        String geminiResponse = aiService.generateVipEntertainment(wordList);

        if (geminiResponse == null) {
            log.error("AIService trả về null cho userId {}", userId);
            return;
        }

        // Validate JSON
        try {
            objectMapper.readTree(geminiResponse);
        } catch (Exception e) {
            log.error("AIService trả về JSON không hợp lệ cho userId {}: {}", userId, geminiResponse);
            return;
        }

        // Lưu vào VipDailyEntertainment
        VipDailyEntertainment entertainment = new VipDailyEntertainment();
        entertainment.setUserId(userId);
        entertainment.setContentJson(geminiResponse);
        entertainment.setIsCompleted(false);
        entertainment.setEntertainmentDate(LocalDate.now());
        entertainment.setCreatedAt(LocalDate.now());
        vipDailyEntertainmentRepository.save(entertainment);

        // Update các từ đã xử lý thành PROCESSED
        for (VipSavedWord word : wordsToProcess) {
            word.setStatus(VipSavedWordStatus.PROCESSED);
            vipSavedWordRepository.save(word);
        }

        log.info("Đã xử lý {} từ cho userId {}.", wordsToProcess.size(), userId);
    }
}