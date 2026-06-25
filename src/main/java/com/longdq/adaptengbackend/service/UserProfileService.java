package com.longdq.adaptengbackend.service;

import com.longdq.adaptengbackend.dto.UserProfileResponseDto;
import com.longdq.adaptengbackend.entity.User;
import com.longdq.adaptengbackend.entity.UserSubscription;
import com.longdq.adaptengbackend.enums.SubscriptionStatus;
import com.longdq.adaptengbackend.repository.UserSubscriptionRepository;
import com.longdq.adaptengbackend.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserSubscriptionRepository userSubscriptionRepository;

    @Transactional(readOnly = true)
    public UserProfileResponseDto getCurrentUserProfile() {
        User user = SecurityUtils.getCurrentUser();
        LocalDateTime now = LocalDateTime.now();

        Optional<UserSubscription> activeSubscription = userSubscriptionRepository
                .findFirstByUserIdAndStatusAndEndDateGreaterThanOrderByEndDateDesc(
                        user.getId(),
                        SubscriptionStatus.ACTIVE,
                        now
                );

        boolean isPremium = activeSubscription.isPresent();
        String packageName = null;
        LocalDateTime premiumEndDate = null;

        if (activeSubscription.isPresent()) {
            UserSubscription subscription = activeSubscription.get();
            packageName = subscription.getSubscriptionPackage().getName();
            premiumEndDate = subscription.getEndDate();
        }

        log.debug("Fetched profile for user: {}, isPremium: {}", user.getEmail(), isPremium);

        return UserProfileResponseDto.builder()
                .email(user.getEmail())
                .fullName(user.getFullName())
                .totalXp(user.getTotalXp())
                .currentLevel(user.getCurrentLevel())
                .isPremium(isPremium)
                .currentPackageName(packageName)
                .premiumEndDate(premiumEndDate)
                .build();
    }
}
