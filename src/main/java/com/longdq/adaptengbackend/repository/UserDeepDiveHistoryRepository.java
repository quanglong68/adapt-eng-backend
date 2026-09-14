package com.longdq.adaptengbackend.repository;

import com.longdq.adaptengbackend.entity.UserDeepDiveHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserDeepDiveHistoryRepository extends JpaRepository<UserDeepDiveHistory, Long> {

    // Trả về true nếu User đã từng làm bộ đề này
    boolean existsByUserIdAndDeepDiveSetId(UUID userId, UUID deepDiveSetId);
}