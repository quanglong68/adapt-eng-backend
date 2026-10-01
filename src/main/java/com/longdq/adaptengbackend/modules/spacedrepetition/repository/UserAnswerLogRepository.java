package com.longdq.adaptengbackend.modules.spacedrepetition.repository;

import com.longdq.adaptengbackend.modules.spacedrepetition.entity.UserAnswerLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserAnswerLogRepository extends JpaRepository<UserAnswerLog, Long> {
}
