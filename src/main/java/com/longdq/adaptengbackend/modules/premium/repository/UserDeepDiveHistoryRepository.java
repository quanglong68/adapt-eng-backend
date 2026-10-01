package com.longdq.adaptengbackend.modules.premium.repository;

import com.longdq.adaptengbackend.modules.premium.entity.UserDeepDiveHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import com.longdq.adaptengbackend.modules.user.entity.User;

@Repository
public interface UserDeepDiveHistoryRepository extends JpaRepository<UserDeepDiveHistory, Long> {

    // Trả về true nếu User đã từng làm bộ đề này
    boolean existsByUserIdAndDeepDiveSetId(UUID userId, UUID deepDiveSetId);
}