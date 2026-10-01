package com.longdq.adaptengbackend.modules.progress.repository;

import com.longdq.adaptengbackend.modules.progress.entity.AppConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppConfigRepository extends JpaRepository<AppConfig, String> {
}