package com.longdq.adaptengbackend.repository;

import com.longdq.adaptengbackend.entity.UserSubscription;
import com.longdq.adaptengbackend.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {

    Optional<UserSubscription> findFirstByUserIdAndStatusAndEndDateGreaterThanOrderByEndDateDesc(
            UUID userId,
            SubscriptionStatus status,
            LocalDateTime now
    );

    boolean existsByUserIdAndStatusAndEndDateGreaterThan(
            UUID userId,
            SubscriptionStatus status,
            LocalDateTime now
    );
}
