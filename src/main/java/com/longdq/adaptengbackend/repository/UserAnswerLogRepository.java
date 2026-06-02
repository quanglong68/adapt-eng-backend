package com.longdq.adaptengbackend.repository;

import com.longdq.adaptengbackend.entity.UserAnswerLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserAnswerLogRepository extends JpaRepository<UserAnswerLog, Long> {
}
