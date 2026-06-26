package com.longdq.adaptengbackend.repository;

import com.longdq.adaptengbackend.entity.VipSavedWord;
import com.longdq.adaptengbackend.enums.VipSavedWordStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VipSavedWordRepository extends JpaRepository<VipSavedWord, Long> {

    List<VipSavedWord> findByUserIdAndStatusOrderByCreatedAtAsc(UUID userId, VipSavedWordStatus status);

    List<VipSavedWord> findByStatusAndCreatedAtBefore(VipSavedWordStatus status, LocalDateTime dateTime);

    List<VipSavedWord> findByStatus(VipSavedWordStatus status);

    long countByUserIdAndStatus(UUID userId, VipSavedWordStatus status);

    Optional<VipSavedWord> findByUserIdAndWordAndStatus(UUID userId, String word, VipSavedWordStatus status);

    void deleteByUserIdAndWordAndStatus(UUID userId, String word, VipSavedWordStatus status);

    @Query("SELECT DISTINCT v.userId FROM VipSavedWord v WHERE v.status = :status")
    List<UUID> findDistinctUserIdsByStatus(VipSavedWordStatus status);
}