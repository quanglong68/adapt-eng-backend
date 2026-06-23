package com.longdq.adaptengbackend.controller;

import com.longdq.adaptengbackend.enums.Level;
import com.longdq.adaptengbackend.scheduler.DailyQuestionInventoryJob;
import com.longdq.adaptengbackend.scheduler.QuestionTestGeneratorJob;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/toeic/hack")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ToeicHackController {

    private final QuestionTestGeneratorJob testGeneratorJob;
    private final DailyQuestionInventoryJob dailyJob;

    // 1. Dùng để MỒI DỮ LIỆU ĐỀ TEST (BƯỚC 2)
    @GetMapping("/gen-monthly-test/{level}")
    public String generateMonthlyTest(@PathVariable Level level) {
        // Chạy ngầm để Postman không bị Timeout khi chờ AI đẻ 50 câu
        new Thread(() -> testGeneratorJob.generateToeicTestForOneLevel(level)).start();
        return "🚀 Đã ra lệnh cho AI đẻ 50 câu Test cấp độ " + level + ". Vui lòng mở Console Spring Boot để xem tiến độ (Khoảng 2-3 phút)!";
    }

    // 2. Dùng để ÉP AI BÙ KHO ĐẠN NỢ SM-2 (BƯỚC 4)
    @GetMapping("/run-night-job")
    public String runNightJob() {
        new Thread(() -> dailyJob.checkAndRefillToeicInventory()).start();
        return "🛠️ Đã kích hoạt Job kiểm kho ban đêm! Vui lòng kiểm tra Console Log để xem hệ thống gọi đạn nợ.";
    }
}