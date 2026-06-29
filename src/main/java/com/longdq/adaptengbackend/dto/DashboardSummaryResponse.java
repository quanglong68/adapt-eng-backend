package com.longdq.adaptengbackend.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class DashboardSummaryResponse {
    private String currentLevel;
    private int streakDays;
    private int totalXP;
    private long dailyMissionCount;
    private List<RecentActivityDto> recentActivities;

    // 🚨 THÊM FIELD NÀY
    private LevelUpProgressDto levelUpProgress;

    @Data
    @Builder
    public static class RecentActivityDto {
        private String label;
        private int score;
        private int total;
        private String color;
        private String time;
    }

    // 🚨 THÊM CLASS NÀY ĐỂ CHỨA DATA THANH TIẾN TRÌNH
    @Data
    @Builder
    public static class LevelUpProgressDto {
        private String targetLevel;
        private boolean isEligibleForBoss;
        private int currentTotalXp;
        private int requiredTotalXp;
        private int current7DayAccuracy;
        private int required7DayAccuracy;
        private boolean isCooldownActive;
        private int daysLeftToRetry;
    }
}