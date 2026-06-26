package com.longdq.adaptengbackend.controller;

import com.longdq.adaptengbackend.scheduler.VipEntertainmentScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/test-jobs")
@RequiredArgsConstructor
public class TestJobController {

    private final VipEntertainmentScheduler vipEntertainmentScheduler;

    @GetMapping("/vip-nightly")
    public String triggerVipNightlyJob() {
        long startTime = System.currentTimeMillis();

        // Gọi thẳng tay vào hàm chạy ngầm của Job 2h sáng
        vipEntertainmentScheduler.processDailyVipEntertainment();

        long endTime = System.currentTimeMillis();
        return "🔥 ĐÃ ÉP CHẠY JOB 2AM THÀNH CÔNG! Thời gian xử lý: " + (endTime - startTime) + "ms. Bác check Database nhé!";
    }
}