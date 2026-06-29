package com.longdq.adaptengbackend.service;

import com.longdq.adaptengbackend.dto.DashboardSummaryResponse;
import com.longdq.adaptengbackend.entity.LevelPromotionConfig;
import com.longdq.adaptengbackend.entity.User;
import com.longdq.adaptengbackend.enums.Level;
import com.longdq.adaptengbackend.enums.TestRecordStatus;
import com.longdq.adaptengbackend.repository.DailyTestRecordRepository;
import com.longdq.adaptengbackend.repository.LevelPromotionConfigRepository;
import com.longdq.adaptengbackend.repository.UserLearningProgressRepository;
import com.longdq.adaptengbackend.repository.UserQuestionHistoryRepository;
import com.longdq.adaptengbackend.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final UserLearningProgressRepository progressRepository;
    private final UserQuestionHistoryRepository historyRepository;
    private final LevelPromotionConfigRepository promotionConfigRepo;
    private final DailyTestRecordRepository recordRepository;

    public DashboardSummaryResponse getDashboardSummary() {
        User user = SecurityUtils.getCurrentUser();
        long dailyMissionCount = progressRepository.countByUserIdAndNextReviewDateLessThanEqual(user.getId(), LocalDateTime.now());

        // ====================================================================
        // 🚨 THUẬT TOÁN STREAK MỚI: CHỈ ĐẾM CÁC NGÀY ĐẠT ĐIỂM >= 10%
        // ====================================================================
        List<LocalDate> activeDates = recordRepository.findValidStreakDates(user.getId());
        int streak = 0;
        LocalDate today = LocalDate.now();

        if (activeDates != null && !activeDates.isEmpty()) {
            LocalDate latestActiveDate = activeDates.get(0);

            // Phải làm bài hôm nay hoặc hôm qua thì mới giữ được chuỗi
            if (latestActiveDate.equals(today) || latestActiveDate.equals(today.minusDays(1))) {
                LocalDate compareDate = latestActiveDate;
                for (LocalDate currentDate : activeDates) {
                    if (currentDate.equals(compareDate)) {
                        streak++;
                        compareDate = compareDate.minusDays(1);
                    } else {
                        break;
                    }
                }
            }
        }

        // ====================================================================
        // LOGIC LẤY HOẠT ĐỘNG GẦN ĐÂY
        // ====================================================================
        List<UserQuestionHistoryRepository.RecentActivityProjection> projections = historyRepository.findRecentActivities(user.getId());
        List<DashboardSummaryResponse.RecentActivityDto> activities = new ArrayList<>();
        for (UserQuestionHistoryRepository.RecentActivityProjection p : projections) {
            double rate = (double) p.getScore() / p.getTotal();
            String color = rate < 0.5 ? "#EF4444" : (rate < 0.8 ? "#F97316" : "#10B981");
            activities.add(DashboardSummaryResponse.RecentActivityDto.builder()
                    .label(p.getLabel() != null ? p.getLabel() : "Luyện tập tổng hợp")
                    .score(p.getScore())
                    .total(p.getTotal())
                    .color(color)
                    .time(p.getAnswerDate().toString())
                    .build());
        }

        // ====================================================================
        // LOGIC GAMIFICATION: KIỂM TRA ĐIỀU KIỆN ĐÁNH BOSS
        // ====================================================================
        DashboardSummaryResponse.LevelUpProgressDto levelUpProgress = null;

        if (user.getCurrentLevel() != null) {
            Level targetLevel = getNextLevel(user.getCurrentLevel());

            if (targetLevel != null) {
                LevelPromotionConfig config = promotionConfigRepo.findById(targetLevel).orElse(null);

                if (config != null) {
                    LocalDate sevenDaysAgo = today.minusDays(7);
                    List<Object[]> stats = recordRepository.getAccuracyStats(
                            user.getId(), user.getCurrentLevel(), sevenDaysAgo, TestRecordStatus.COMPLETED);

                    int currentAccuracy = 0;
                    if (!stats.isEmpty() && stats.get(0)[0] != null && stats.get(0)[1] != null) {
                        long totalScore = ((Number) stats.get(0)[0]).longValue();
                        long totalQs = ((Number) stats.get(0)[1]).longValue();
                        if (totalQs > 0) currentAccuracy = (int) ((totalScore * 100) / totalQs);
                    }

                    boolean isCooldownActive = false;
                    int daysLeft = 0;
                    if (user.getLastLevelUpTestDate() != null) {
                        LocalDate nextAllowed = user.getLastLevelUpTestDate().plusDays(config.getCooldownDays());
                        if (today.isBefore(nextAllowed)) {
                            isCooldownActive = true;
                            daysLeft = (int) ChronoUnit.DAYS.between(today, nextAllowed);
                        }
                    }

                    boolean isEligible = !isCooldownActive &&
                            user.getTotalXp() >= config.getRequiredTotalXp() &&
                            currentAccuracy >= config.getRequired7DayAccuracy();

                    levelUpProgress = DashboardSummaryResponse.LevelUpProgressDto.builder()
                            .targetLevel(targetLevel.name())
                            .isEligibleForBoss(isEligible)
                            .currentTotalXp(user.getTotalXp())
                            .requiredTotalXp(config.getRequiredTotalXp())
                            .current7DayAccuracy(currentAccuracy)
                            .required7DayAccuracy(config.getRequired7DayAccuracy())
                            .isCooldownActive(isCooldownActive)
                            .daysLeftToRetry(daysLeft)
                            .build();
                }
            }
        }

        return DashboardSummaryResponse.builder()
                .currentLevel(user.getCurrentLevel() != null ? user.getCurrentLevel().name() : "CHƯA_XÁC_ĐỊNH")
                .streakDays(streak) // Streak đã chuẩn chỉ
                .totalXP(user.getTotalXp())
                .dailyMissionCount(dailyMissionCount)
                .recentActivities(activities)
                .levelUpProgress(levelUpProgress)
                .build();
    }

    private Level getNextLevel(Level currentLevel) {
        switch (currentLevel) {
            case A1: return Level.A2;
            case A2: return Level.B1;
            case B1: return Level.B2;
            case B2: return Level.C1;
            case C1: return Level.C2;
            default: return null;
        }
    }
}