package com.longdq.adaptengbackend.repository;

import com.longdq.adaptengbackend.entity.UserWritingQuestionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserWritingQuestionHistoryRepository extends JpaRepository<UserWritingQuestionHistory, Long> {
    // Phase 1 + Phase 2 chỉ dùng saveAll() kế thừa từ JpaRepository.
    // Các truy vấn bốc đề "chưa từng làm" / "làm lâu nhất" nằm trong WritingQuestionRepository dạng native query.
}
