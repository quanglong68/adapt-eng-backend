package com.longdq.adaptengbackend.repository;

import com.longdq.adaptengbackend.entity.VipDailyEntertainment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VipDailyEntertainmentRepository extends JpaRepository<VipDailyEntertainment, Long> {

    Optional<VipDailyEntertainment> findByUserIdAndEntertainmentDate(UUID userId, LocalDate date);

    List<VipDailyEntertainment> findByIsCompleted(Boolean isCompleted);

    List<VipDailyEntertainment> findByUserIdAndIsCompleted(UUID userId, Boolean isCompleted);

    boolean existsByUserIdAndIsCompletedAndEntertainmentDate(UUID userId, Boolean isCompleted, LocalDate date);

    boolean existsByUserIdAndIsCompleted(UUID userId, Boolean isCompleted);
    Optional<VipDailyEntertainment> findFirstByUserIdAndIsCompletedOrderByIdAsc(UUID userId, Boolean isCompleted);
}