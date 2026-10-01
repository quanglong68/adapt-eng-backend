package com.longdq.adaptengbackend.common.security;

import com.longdq.adaptengbackend.common.enums.SubscriptionStatus;
import com.longdq.adaptengbackend.common.exception.ForbiddenException;
import com.longdq.adaptengbackend.modules.user.entity.User;
import com.longdq.adaptengbackend.modules.payment.repository.UserSubscriptionRepository;
import com.longdq.adaptengbackend.common.security.SecurityUtils;
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


    @Before("@within(com.longdq.adaptengbackend.common.security.RequirePremium) || " +
            "@annotation(com.longdq.adaptengbackend.common.security.RequirePremium)")
    public void checkPremiumAccess() {
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