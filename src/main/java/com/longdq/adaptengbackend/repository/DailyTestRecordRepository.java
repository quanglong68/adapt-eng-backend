package com.longdq.adaptengbackend.repository;

import com.longdq.adaptengbackend.entity.DailyTestRecord;
import com.longdq.adaptengbackend.enums.Level;
import com.longdq.adaptengbackend.enums.TestRecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailyTestRecordRepository extends JpaRepository<DailyTestRecord, Long> {
    @Query("SELECT DISTINCT d.testDate FROM DailyTestRecord d " +
            "WHERE d.userId = :userId AND d.status = 'COMPLETED' " +
            "AND (d.score * 100 / d.totalQuestions) >= 10 " +
            "ORDER BY d.testDate DESC")
    List<LocalDate> findValidStreakDates(@org.springframework.data.repository.query.Param("userId") java.util.UUID userId);
    @Query("SELECT SUM(d.score), SUM(d.totalQuestions) " +
            "FROM DailyTestRecord d " +
            "WHERE d.userId = :userId AND d.level = :level AND d.testDate >= :startDate " +
            "AND d.status = :status")
    List<Object[]> getAccuracyStats(
            @Param("userId") java.util.UUID userId,
            @Param("level") Level level,
            @Param("startDate") LocalDate startDate,
            @Param("status") TestRecordStatus status
    );
    // 1. Tìm đề ĐANG LÀM DỞ của ngày hôm nay (An toàn vì IN_PROGRESS mỗi ngày chỉ có tối đa 1 cái)
    Optional<DailyTestRecord> findFirstByUserIdAndTestDateAndStatus(UUID userId, LocalDate testDate, TestRecordStatus status);

    // 2. Đếm số lượng đề ĐÃ HOÀN THÀNH trong hôm nay
    long countByUserIdAndTestDateAndStatus(UUID userId, LocalDate testDate, TestRecordStatus status);

    // 3. Dọn rác đề cũ
    List<DailyTestRecord> findByUserIdAndStatusAndTestDateLessThan(UUID userId, TestRecordStatus status, LocalDate testDate);

    // 4. Lấy lịch sử
    List<DailyTestRecord> findByUserIdAndStatusOrderByTestDateDesc(UUID userId, TestRecordStatus status);
}