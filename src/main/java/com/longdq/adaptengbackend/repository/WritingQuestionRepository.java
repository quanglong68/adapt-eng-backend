package com.longdq.adaptengbackend.repository;

import com.longdq.adaptengbackend.entity.WritingQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WritingQuestionRepository extends JpaRepository<WritingQuestion, Long> {

    // Bốc ngẫu nhiên N câu hỏi theo Part (Dùng native query vì ORDER BY RANDOM là lệnh của DB)
    @Query(value = "SELECT * FROM writing_questions WHERE toeic_part = :part ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<WritingQuestion> findRandomByToeicPart(@Param("part") String part, @Param("limit") int limit);
}