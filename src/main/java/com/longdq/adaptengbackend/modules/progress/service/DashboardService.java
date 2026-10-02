package com.longdq.adaptengbackend.modules.progress.service;

import com.longdq.adaptengbackend.modules.progress.dto.DashboardSummaryResponse;
import com.longdq.adaptengbackend.modules.progress.entity.LevelPromotionConfig;
import com.longdq.adaptengbackend.modules.user.entity.User;
import com.longdq.adaptengbackend.common.enums.Level;
import com.longdq.adaptengbackend.common.enums.TestRecordStatus;
import com.longdq.adaptengbackend.common.enums.ToeicPart;
import com.longdq.adaptengbackend.modules.toeic.repository.DailyTestRecordRepository;
import com.longdq.adaptengbackend.modules.progress.repository.LevelPromotionConfigRepository;
import com.longdq.adaptengbackend.modules.spacedrepetition.repository.UserLearningProgressRepository;
import com.longdq.adaptengbackend.modules.toeic.repository.UserQuestionHistoryRepository;
import com.longdq.adaptengbackend.modules.writing.repository.WritingTestRecordRepository;
import com.longdq.adaptengbackend.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final UserLearningProgressRepository progressRepository;
    private final UserQuestionHistoryRepository historyRepository;
    private final LevelPromotionConfigRepository promotionConfigRepo;
    private final DailyTestRecordRepository recordRepository;
    private final WritingTestRecordRepository writingRecordRepository;

    public DashboardSummaryResponse getDashboardSummary() {
        User user = SecurityUtils.getCurrentUser();
        LocalDateTime now = LocalDateTime.now();
        long dailyMissionCount = progressRepository.countByUserIdAndNextReviewDateLessThanEqual(user.getId(), now);

        // Tách số liệu nhiệm vụ theo kỹ năng: Writing đếm riêng từng Part,
        // Reading = tổng trừ Writing (kèm cả record cũ chưa có toeicPart để khỏi lệch số liệu tổng).
        long writingPart1Count = progressRepository.countByUserIdAndToeicPartAndNextReviewDateLessThanEqual(
                user.getId(), ToeicPart.WRITING_PART_1, now);
        long writingPart2Count = progressRepository.countByUserIdAndToeicPartAndNextReviewDateLessThanEqual(
                user.getId(), ToeicPart.WRITING_PART_2, now);
        long writingPart3Count = progressRepository.countByUserIdAndToeicPartAndNextReviewDateLessThanEqual(
                user.getId(), ToeicPart.WRITING_PART_3, now);
        long writingMissionCount = writingPart1Count + writingPart2Count + writingPart3Count;
        long readingMissionCount = Math.max(0, dailyMissionCount - writingMissionCount);

        // ====================================================================
        // 🚨 THUẬT TOÁN STREAK MỚI: CHỈ ĐẾM CÁC NGÀY ĐẠT ĐIỂM >= 10%
        // Gộp cả luyện tập Reading (daily_test_records) và Writing (writing_test_records)
        // để user chỉ cần làm bài Writing cũng giữ được chuỗi.
        // ====================================================================
        List<LocalDate> activeDates = Stream
                .concat(recordRepository.findValidStreakDates(user.getId()).stream(),
                        writingRecordRepository.findValidStreakDates(user.getId(), "DAILY_TEST").stream())
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
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
                .writingMissionCount(writingMissionCount)
                .writingPart1Count(writingPart1Count)
                .writingPart2Count(writingPart2Count)
                .writingPart3Count(writingPart3Count)
                .readingMissionCount(readingMissionCount)
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