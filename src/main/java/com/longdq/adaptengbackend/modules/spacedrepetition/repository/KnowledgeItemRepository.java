package com.longdq.adaptengbackend.modules.spacedrepetition.repository;

import com.longdq.adaptengbackend.modules.spacedrepetition.entity.KnowledgeItem;
import com.longdq.adaptengbackend.common.enums.KnowledgeType;
import com.longdq.adaptengbackend.common.enums.Level;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface KnowledgeItemRepository extends JpaRepository<KnowledgeItem, UUID> {
    Optional<KnowledgeItem> findByKnowledgeTypeAndLevel(KnowledgeType knowledgeType, Level level);
}
