package com.longdq.adaptengbackend.common.config;

import com.longdq.adaptengbackend.modules.progress.entity.AppConfig;
import com.longdq.adaptengbackend.modules.progress.entity.LevelPromotionConfig;
import com.longdq.adaptengbackend.common.enums.Level;
import com.longdq.adaptengbackend.modules.progress.repository.AppConfigRepository;
import com.longdq.adaptengbackend.modules.progress.repository.LevelPromotionConfigRepository;
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

        // 1. CHÈN DỮ LIỆU CẤU HÌNH HỆ THỐNG (APP CONFIGS)
        log.info("Checking and seeding AppConfig data...");
        List<AppConfig> defaultConfigs = List.of(
                new AppConfig("MIN_PRACTICE_SCORE_PERCENT", "10"),

                // --- Cấu hình cho tính năng VIP Deep Dive ---
                new AppConfig("VIP_DEEP_DIVE_DAILY_QUOTA", "3"),
                new AppConfig("DEEP_DIVE_EXCELLENT_THRESHOLD", "80"),
                new AppConfig("DEEP_DIVE_GOOD_THRESHOLD", "50"),
                new AppConfig("DEEP_DIVE_EXCELLENT_EF_BONUS", "0.3"),
                new AppConfig("DEEP_DIVE_GOOD_EF_BONUS", "0.2"),
                new AppConfig("DEEP_DIVE_POOR_EF_BONUS", "0.1"),
                new AppConfig("DEEP_DIVE_POOR_INTERVAL", "1")
        );

        for (AppConfig config : defaultConfigs) {
            // Kiểm tra xem Key này đã có trong DB chưa, nếu chưa thì mới lưu
            if (!appConfigRepository.existsById(config.getConfigKey())) {
                appConfigRepository.save(config);
                log.info("Seeded config: {}", config.getConfigKey());
            }
        }

        // 2. CHÈN CẤU HÌNH THĂNG CẤP THEO CHUẨN CAMBRIDGE (LEVEL PROMOTIONS)
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