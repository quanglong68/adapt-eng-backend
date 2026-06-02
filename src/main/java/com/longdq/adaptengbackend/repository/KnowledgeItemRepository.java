package com.longdq.adaptengbackend.repository;

import com.longdq.adaptengbackend.entity.KnowledgeItem;
import com.longdq.adaptengbackend.enums.KnowledgeType;
import com.longdq.adaptengbackend.enums.Level;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface KnowledgeItemRepository extends JpaRepository<KnowledgeItem, UUID> {
    Optional<KnowledgeItem> findByKnowledgeTypeAndLevel(KnowledgeType knowledgeType, Level level);
}
