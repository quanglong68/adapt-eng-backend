package com.longdq.adaptengbackend.modules.premium.scheduler;

import com.longdq.adaptengbackend.modules.premium.entity.VipDailyEntertainment;
import com.longdq.adaptengbackend.modules.premium.entity.VipSavedWord;
import com.longdq.adaptengbackend.common.enums.SubscriptionStatus;
import com.longdq.adaptengbackend.common.enums.VipSavedWordStatus;
import com.longdq.adaptengbackend.modules.payment.repository.UserSubscriptionRepository;
import com.longdq.adaptengbackend.modules.premium.repository.VipDailyEntertainmentRepository;
import com.longdq.adaptengbackend.modules.premium.repository.VipSavedWordRepository;
import com.longdq.adaptengbackend.modules.premium.service.VipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VipEntertainmentScheduler {

    private final VipSavedWordRepository vipSavedWordRepository;
    private final VipDailyEntertainmentRepository vipDailyEntertainmentRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final VipService vipService;

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
        // Tái dùng lõi sinh đề của VipService (giữ nguyên hành vi job 2h sáng)
        List<VipSavedWord> pendingWords = vipSavedWordRepository
                .findByUserIdAndStatusOrderByCreatedAtAsc(userId, VipSavedWordStatus.PENDING);

        if (pendingWords.isEmpty()) {
            return;
        }

        boolean ok = vipService.generateAndSaveEntertainment(userId, pendingWords);
        if (ok) {
            log.info("Đã xử lý đề giải trí cho userId {}.", userId);
        }
    }
}