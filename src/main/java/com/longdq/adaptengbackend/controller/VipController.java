package com.longdq.adaptengbackend.controller;

import com.longdq.adaptengbackend.annotation.RequirePremium;
import com.longdq.adaptengbackend.dto.*;
import com.longdq.adaptengbackend.service.VipService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/vip")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@RequirePremium
public class VipController {

    private final VipService vipService;

    @PostMapping("/save-word")
    public ResponseEntity<VipActionResponseDto> saveWord(@RequestBody Map<String, String> request) {
        String word = request.get("word");

        if (word == null || word.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    VipActionResponseDto.builder()
                            .success(false)
                            .message("Từ không được để trống.")
                            .build()
            );
        }

        // Dọn dẹp: Cắt khoảng trắng và dấu câu thừa ở 2 đầu (VD: " Hello, " -> "Hello")
        word = word.trim().replaceAll("^[^a-zA-Z0-9]+|[^a-zA-Z0-9]+$", "").toLowerCase();

        // Đếm số từ
        int wordCount = word.split("\\s+").length;

        // BỘ LỌC THÉP: Độ dài 2-30 ký tự, Tối đa 3 từ, Chỉ chứa chữ cái/số/khoảng trắng/gạch nối
        if (word.length() < 2 || word.length() > 30 || wordCount > 3 || !word.matches("^[a-z0-9\\s\\-']+$")) {
            return ResponseEntity.badRequest().body(
                    VipActionResponseDto.builder()
                            .success(false)
                            .message("Từ vựng không hợp lệ! (Chỉ nhận tối đa 3 từ, không chứa ký tự đặc biệt)")
                            .build()
            );
        }

        return ResponseEntity.ok(vipService.saveWord(word));
    }

    @DeleteMapping("/remove-word")
    public ResponseEntity<VipActionResponseDto> removeWord(@RequestParam String word) {
        return ResponseEntity.ok(vipService.removeWord(word));
    }

    @GetMapping("/pending-words")
    public ResponseEntity<List<VipPendingWordDto>> getPendingWords() {
        return ResponseEntity.ok(vipService.getPendingWords());
    }

    @GetMapping("/daily-entertainment")
    public ResponseEntity<VipEntertainmentResponseDto> getDailyEntertainment() {
        return ResponseEntity.ok(vipService.getDailyEntertainment());
    }

    @PostMapping("/complete-story")
    public ResponseEntity<VipActionResponseDto> completeStory() {
        return ResponseEntity.ok(vipService.completeStory());
    }

    @GetMapping("/check-fomo")
    public ResponseEntity<VipFomoResponseDto> checkFomo() {
        return ResponseEntity.ok(vipService.checkFomo());
    }
}