package com.longdq.adaptengbackend.repository;

import com.longdq.adaptengbackend.entity.UserQuestionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserQuestionHistoryRepository extends JpaRepository<UserQuestionHistory, Long> {

}