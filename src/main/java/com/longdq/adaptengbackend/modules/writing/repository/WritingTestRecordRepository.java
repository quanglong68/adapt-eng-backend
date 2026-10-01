package com.longdq.adaptengbackend.modules.writing.repository;

import com.longdq.adaptengbackend.modules.writing.entity.WritingTestRecord;
import com.longdq.adaptengbackend.common.enums.TestRecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WritingTestRecordRepository extends JpaRepository<WritingTestRecord, Long> {

    // Tìm bài test đang làm dở (IN_PROGRESS) theo loại bài test (PLACEMENT_TEST / DAILY_TEST)
    Optional<WritingTestRecord> findByUserIdAndTestTypeAndStatus(UUID userId, String testType, TestRecordStatus status);

    // ==========================================
    // NHÓM TRUY VẤN DÀNH RIÊNG CHO WRITING PRACTICE (DAILY_TEST)
    // Quota được đếm RIÊNG trên bảng này, không dùng chung với Reading (daily_test_records)
    // ==========================================

    // 1. Tìm đề ĐANG LÀM DỞ của ngày hôm nay (an toàn vì IN_PROGRESS mỗi ngày chỉ có tối đa 1 cái)
    Optional<WritingTestRecord> findFirstByUserIdAndTestTypeAndTestDateAndStatus(
            UUID userId, String testType, LocalDate testDate, TestRecordStatus status);

    // 2. Đếm số đề ĐÃ HOÀN THÀNH hôm nay (dùng cho quota Free 1 / VIP 3)
    long countByUserIdAndTestTypeAndTestDateAndStatus(
            UUID userId, String testType, LocalDate testDate, TestRecordStatus status);

    // 3. Dọn rác các đề làm dở của những ngày trước
    List<WritingTestRecord> findByUserIdAndTestTypeAndStatusAndTestDateLessThan(
            UUID userId, String testType, TestRecordStatus status, LocalDate testDate);

    // 4. Lấy lịch sử luyện tập Writing
    List<WritingTestRecord> findByUserIdAndTestTypeOrderByTestDateDesc(UUID userId, String testType);

    // 5. Các ngày đạt chuẩn để tính Streak (chỉ tính điểm luyện tập, không tính placement test)
    // Thang điểm Writing là 0-3 mỗi câu nên phải chia cho (totalQuestions * 3) thay vì totalQuestions
    @Query("SELECT DISTINCT w.testDate FROM WritingTestRecord w " +
            "WHERE w.userId = :userId AND w.testType = :testType AND w.status = 'COMPLETED' " +
            "AND w.totalQuestions IS NOT NULL AND w.totalQuestions > 0 " +
            "AND (w.score * 100) / (w.totalQuestions * 3) >= 10 " +
            "ORDER BY w.testDate DESC")
    List<LocalDate> findValidStreakDates(
            @Param("userId") UUID userId,
            @Param("testType") String testType);
}