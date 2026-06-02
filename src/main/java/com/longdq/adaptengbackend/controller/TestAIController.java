package com.longdq.adaptengbackend.controller;

import com.longdq.adaptengbackend.enums.Level;
import com.longdq.adaptengbackend.service.AIService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestAIController {

    private final AIService aiService;

    @GetMapping("/generate-questions")
    public ResponseEntity<String> testGenerateQuestions() {
        System.out.println("Đang gọi AI Gemini, vui lòng đợi...");

        String jsonResult = aiService.generateTestQuestions(Level.A2);

        // Nếu AI bị quá tải hoặc lỗi, jsonResult sẽ là null
        if (jsonResult == null || jsonResult.isEmpty()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Google Gemini đang bị quá tải hoặc gặp lỗi. Vui lòng xem log ở Console IntelliJ và thử lại sau.");
        }

        System.out.println("AI đã trả về kết quả thành công!");
        return ResponseEntity.ok(jsonResult);
    }
}