package com.longdq.adaptengbackend.util;

import com.longdq.adaptengbackend.enums.SubscriptionStatus;
import com.longdq.adaptengbackend.repository.UserSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PremiumCheckUtil {

    private final UserSubscriptionRepository userSubscriptionRepository;

    public boolean isPremiumUser(UUID userId) {
        if (userId == null) return false;

        // Kiểm tra xem có gói VIP nào đang ACTIVE và chưa hết hạn không
        return userSubscriptionRepository.existsByUserIdAndStatusAndEndDateGreaterThan(
                userId,
                SubscriptionStatus.ACTIVE,
                LocalDateTime.now()
        );
    }
}