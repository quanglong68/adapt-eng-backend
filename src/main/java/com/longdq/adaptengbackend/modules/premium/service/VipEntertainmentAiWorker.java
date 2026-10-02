package com.longdq.adaptengbackend.modules.premium.service;

import com.longdq.adaptengbackend.common.enums.NotificationType;
import com.longdq.adaptengbackend.common.enums.VipSavedWordStatus;
import com.longdq.adaptengbackend.modules.notification.entity.Notification;
import com.longdq.adaptengbackend.modules.notification.service.NotificationService;
import com.longdq.adaptengbackend.modules.premium.entity.VipSavedWord;
import com.longdq.adaptengbackend.modules.premium.repository.VipSavedWordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Sinh đề giải trí VIP (Tarot) theo yêu cầu của user (không chờ job 2h sáng).
 * Tái dùng lõi sinh đề của {@link VipService}, xong thì bắn notification realtime.
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @__(@Lazy))
public class VipEntertainmentAiWorker {

    private final VipService vipService;
    private final VipSavedWordRepository vipSavedWordRepository;
    private final NotificationService notificationService;
    private final EntertainmentGenerationRegistry generationRegistry;

    @Async
    public void generateAsync(UUID userId, List<Long> wordIds) {
        try {
            // Chỉ dùng những từ vẫn còn PENDING (user có thể đã xóa trong lúc chờ)
            List<VipSavedWord> words = vipSavedWordRepository.findAllById(wordIds).stream()
                    .filter(w -> w.getUserId().equals(userId) && w.getStatus() == VipSavedWordStatus.PENDING)
                    .collect(Collectors.toList());

            boolean ok = vipService.generateAndSaveEntertainment(userId, words);
            if (!ok) {
                log.warn("Sinh đề giải trí on-demand thất bại cho userId {} (AI lỗi hoặc hết từ).", userId);
                return;
            }

            Notification notif = Notification.builder()
                    .userId(userId)
                    .title("Đề Tarot đã sẵn sàng!")
                    .message("AI đã dệt xong vận mệnh hôm nay từ những từ bạn đã lưu. Bấm vào khám phá ngay nhé!")
                    .type(NotificationType.VIP_ENTERTAINMENT_READY)
                    .actionUrl("/vip-entertainment")
                    .isRead(false)
                    .build();

            notificationService.sendNotification(userId, notif);
            log.info("Đã sinh xong đề giải trí on-demand cho userId {}.", userId);
        } catch (Exception e) {
            log.error("Lỗi tiến trình ngầm sinh đề giải trí cho userId {}: ", userId, e);
        } finally {
            generationRegistry.release(userId);
        }
    }
}
