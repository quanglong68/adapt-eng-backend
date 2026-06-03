package com.longdq.adaptengbackend.repository;

import com.longdq.adaptengbackend.entity.Question;
import com.longdq.adaptengbackend.enums.Level;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findByIsAnswerFalse();

    List<Question> findByKnowledgeItemIdIn(List<UUID> knowledgeItemIds);

    @Query(value = """
    SELECT *
    FROM questions q
    WHERE q.level = :#{#level.name()}
      AND q.purpose = 'TEST'
    ORDER BY RANDOM()
    LIMIT 30
""", nativeQuery = true)
    List<Question> find30RandomTestQuestionsByLevel(@Param("level") Level level);
}
