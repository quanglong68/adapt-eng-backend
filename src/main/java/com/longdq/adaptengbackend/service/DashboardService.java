package com.longdq.adaptengbackend.service;

import com.longdq.adaptengbackend.dto.DashboardSummaryResponse;
import com.longdq.adaptengbackend.entity.User;
import com.longdq.adaptengbackend.repository.UserLearningProgressRepository;
import com.longdq.adaptengbackend.repository.UserQuestionHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final UserLearningProgressRepository progressRepository;
    private final UserQuestionHistoryRepository historyRepository;

    public DashboardSummaryResponse getDashboardSummary() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        // 1. Lấy số nhiệm vụ cần làm hôm nay (Bản ghi có lịch review <= thời điểm hiện tại)
        long dailyMissionCount = progressRepository.countByUserIdAndNextReviewDateLessThanEqual(user.getId(), LocalDateTime.now());

        // 2. Thuật toán tính Streak ngày học liên tiếp thực tế từ DB
        List<java.sql.Date> activeDates = historyRepository.findDistinctAnsweredDates(user.getId());
        int streak = 0;
        LocalDate today = LocalDate.now();

        if (!activeDates.isEmpty()) {
            LocalDate latestActiveDate = activeDates.get(0).toLocalDate();
            // Nếu ngày gần nhất là hôm nay hoặc hôm qua thì mới tính tiếp chuỗi
            if (latestActiveDate.equals(today) || latestActiveDate.equals(today.minusDays(1))) {
                LocalDate compareDate = latestActiveDate;
                for (java.sql.Date sqlDate : activeDates) {
                    LocalDate currentProgressDate = sqlDate.toLocalDate();
                    if (currentProgressDate.equals(compareDate)) {
                        streak++;
                        compareDate = compareDate.minusDays(1); // Lùi 1 ngày để đối chiếu vòng lặp sau
                    } else {
                        break; // Bị đứt chuỗi ngày liên tiếp
                    }
                }
            }
        }

        // 3. Lấy và ánh xạ 5 hoạt động gần nhất từ Projection sang DTO
        List<UserQuestionHistoryRepository.RecentActivityProjection> projections = historyRepository.findRecentActivities(user.getId());
        List<DashboardSummaryResponse.RecentActivityDto> activities = new ArrayList<>();

        for (UserQuestionHistoryRepository.RecentActivityProjection p : projections) {
            // Tính toán màu sắc hiển thị động dựa trên tỷ lệ làm đúng bài tập
            double rate = (double) p.getScore() / p.getTotal();
            String color = "#10B981"; // Xanh lá (Đạt yêu cầu)
            if (rate < 0.5) color = "#EF4444"; // Đỏ (Yếu)
            else if (rate < 0.8) color = "#F97316"; // Cam (Trung bình)

            activities.add(DashboardSummaryResponse.RecentActivityDto.builder()
                    .label(p.getLabel() != null ? p.getLabel() : "Luyện tập tổng hợp")
                    .score(p.getScore())
                    .total(p.getTotal())
                    .color(color)
                    .time(p.getAnswerDate().toString())
                    .build());
        }

        return DashboardSummaryResponse.builder()
                .currentLevel(user.getCurrentLevel() != null ? user.getCurrentLevel().name() : "CHƯA_XÁC_ĐỊNH")
                .streakDays(streak)
                .totalXP(user.getTotalXp()) // Lấy từ trường vừa thêm ở Bước 1
                .dailyMissionCount(dailyMissionCount)
                .recentActivities(activities)
                .build();
    }
}