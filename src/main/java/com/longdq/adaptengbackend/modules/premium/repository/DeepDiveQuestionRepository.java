package com.longdq.adaptengbackend.modules.premium.repository;

import com.longdq.adaptengbackend.modules.premium.entity.DeepDiveQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DeepDiveQuestionRepository extends JpaRepository<DeepDiveQuestion, Long> {
    List<DeepDiveQuestion> findByDeepDiveSetId(UUID setId);
}