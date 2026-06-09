package com.longdq.adaptengbackend.controller;

import com.longdq.adaptengbackend.scheduler.DailyQuestionInventoryJob;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
public class DevTestController {

    private final DailyQuestionInventoryJob job;

    @PostMapping("/trigger-refill")
    public ResponseEntity<String> triggerRefill() {
        // Gọi thẳng hàm của Job
        new Thread(job::checkAndRefillQuestionInventory).start();
        return ResponseEntity.ok("Tiến trình nạp kho đã bắt đầu chạy ngầm...");
    }
}