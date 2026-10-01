package com.longdq.adaptengbackend.modules.premium.repository;

import com.longdq.adaptengbackend.modules.premium.entity.DeepDiveSet;
import com.longdq.adaptengbackend.common.enums.KnowledgeType;
import com.longdq.adaptengbackend.common.enums.Level;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DeepDiveSetRepository extends JpaRepository<DeepDiveSet, UUID> {

    @Query("SELECT d FROM DeepDiveSet d WHERE d.knowledgeType = :type AND " +
            "(:word IS NULL OR d.targetWord = :word) AND d.level = :level")
    List<DeepDiveSet> findMatchingSets(@Param("type") KnowledgeType type,
                                       @Param("word") String word,
                                       @Param("level") Level level);
}