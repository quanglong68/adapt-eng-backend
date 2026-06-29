package com.longdq.adaptengbackend.repository;

import com.longdq.adaptengbackend.entity.LevelPromotionConfig;
import com.longdq.adaptengbackend.enums.Level;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LevelPromotionConfigRepository extends JpaRepository<LevelPromotionConfig, Level> {
}