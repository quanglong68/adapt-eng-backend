package com.longdq.adaptengbackend.aspect;

import com.longdq.adaptengbackend.annotation.RequirePremium;
import com.longdq.adaptengbackend.enums.SubscriptionStatus;
import com.longdq.adaptengbackend.exception.ForbiddenException;
import com.longdq.adaptengbackend.entity.User;
import com.longdq.adaptengbackend.repository.UserSubscriptionRepository;
import com.longdq.adaptengbackend.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class PremiumGuardAspect {

    private final UserSubscriptionRepository userSubscriptionRepository;

    @Before("@annotation(requirePremium)")
    public void checkPremiumAccess(RequirePremium requirePremium) {
        User user = SecurityUtils.getCurrentUser();
        boolean hasPremium = userSubscriptionRepository.existsByUserIdAndStatusAndEndDateGreaterThan(
                user.getId(),
                SubscriptionStatus.ACTIVE,
                LocalDateTime.now()
        );

        if (!hasPremium) {
            log.warn("Premium access denied for user: {}", user.getEmail());
            throw new ForbiddenException("Tính năng VIP yêu cầu gói Premium đang hoạt động.");
        }
    }
}
