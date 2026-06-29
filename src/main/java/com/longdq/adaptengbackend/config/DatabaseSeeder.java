package com.longdq.adaptengbackend.config;

import com.longdq.adaptengbackend.entity.AppConfig;
import com.longdq.adaptengbackend.entity.LevelPromotionConfig;
import com.longdq.adaptengbackend.enums.Level;
import com.longdq.adaptengbackend.repository.AppConfigRepository;
import com.longdq.adaptengbackend.repository.LevelPromotionConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseSeeder implements CommandLineRunner {

    private final AppConfigRepository appConfigRepository;
    private final LevelPromotionConfigRepository levelPromotionConfigRepository;

    @Override
    public void run(String... args) throws Exception {
        // 1. Chèn cấu hình AppConfig (Quy định 10% chống Spam)
        if (appConfigRepository.count() == 0) {
            log.info("Seeding AppConfig data...");
            appConfigRepository.save(new AppConfig("MIN_PRACTICE_SCORE_PERCENT", "10"));
        }

        // 2. Chèn cấu hình Thăng cấp theo chuẩn Cambridge XP
        if (levelPromotionConfigRepository.count() == 0) {
            log.info("Seeding LevelPromotionConfig data...");
            List<LevelPromotionConfig> configs = List.of(
                    new LevelPromotionConfig(Level.A1, 10000, 85, 65, 7),
                    new LevelPromotionConfig(Level.A2, 20000, 85, 65, 7),
                    new LevelPromotionConfig(Level.B1, 40000, 85, 65, 7),
                    new LevelPromotionConfig(Level.B2, 60000, 85, 65, 7),
                    new LevelPromotionConfig(Level.C1, 80000, 85, 65, 7),
                    new LevelPromotionConfig(Level.C2, 120000, 85, 65, 7)
            );
            levelPromotionConfigRepository.saveAll(configs);
        }
    }
}