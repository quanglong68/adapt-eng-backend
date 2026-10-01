package com.longdq.adaptengbackend.modules.premium.repository;

import com.longdq.adaptengbackend.modules.premium.entity.DeepDiveSession;
import com.longdq.adaptengbackend.common.enums.DeepDiveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeepDiveSessionRepository extends JpaRepository<DeepDiveSession, UUID> {
    long countByUserIdAndCreatedAtGreaterThanEqual(UUID userId, LocalDateTime startOfDay);

    // Tìm 1 Session gần nhất đang ở trạng thái GENERATING hoặc READY
    Optional<DeepDiveSession> findFirstByUserIdAndKnowledgeItemIdAndTargetWordAndStatusInOrderByCreatedAtDesc(
            UUID userId,
            UUID knowledgeItemId,
            String targetWord,
            List<DeepDiveStatus> statuses
    );
}