package com.longdq.adaptengbackend.repository;

import com.longdq.adaptengbackend.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findByIsAnswerFalse();
    // Giả sử ID của KnowledgeItem là kiểu UUID (hoặc String/Long tùy bạn thiết kế)
    List<Question> findByKnowledgeItemIdIn(List<UUID> knowledgeItemIds);
}
