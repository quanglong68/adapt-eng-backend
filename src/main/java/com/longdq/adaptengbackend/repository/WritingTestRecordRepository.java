package com.longdq.adaptengbackend.repository;

import com.longdq.adaptengbackend.entity.WritingTestRecord;
import com.longdq.adaptengbackend.enums.TestRecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WritingTestRecordRepository extends JpaRepository<WritingTestRecord, Long> {

    // Tìm bài test đang làm dở (IN_PROGRESS) theo loại bài test (PLACEMENT_TEST / DAILY_TEST)
    Optional<WritingTestRecord> findByUserIdAndTestTypeAndStatus(UUID userId, String testType, TestRecordStatus status);
}