package com.longdq.adaptengbackend.modules.premium.controller;

import com.longdq.adaptengbackend.modules.premium.dto.DeepDiveDto;
import com.longdq.adaptengbackend.modules.premium.service.VipDeepDiveService;
import com.longdq.adaptengbackend.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vip/deep-dive")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class VipDeepDiveController {

    private final VipDeepDiveService deepDiveService;

    // 1. API: Lấy Top 10 Chủ điểm yếu nhất (skill = WRITING | READING | ALL)
    @GetMapping("/recommendations")
    public ResponseEntity<List<DeepDiveDto.RecommendationResponse>> getRecommendations(
            @RequestParam(name = "skill", required = false, defaultValue = "ALL") String skill) {
        UUID userId = SecurityUtils.getCurrentUser().getId();
        return ResponseEntity.ok(deepDiveService.getTopWeaknesses(userId, skill));
    }

    // 2. API: Bấm nút "Ôn tập" -> Sinh Session và gọi AI ngầm
    @PostMapping("/init")
    public ResponseEntity<DeepDiveDto.InitResponse> initSession(@RequestBody DeepDiveDto.InitRequest request) {
        UUID userId = SecurityUtils.getCurrentUser().getId();
        return ResponseEntity.ok(deepDiveService.initSession(
                userId, request.getKnowledgeItemId(), request.getTargetWord()
        ));
    }


    @GetMapping("/{sessionId}/questions")
    public ResponseEntity<List<DeepDiveDto.PassageResponse>> getSessionQuestions(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(deepDiveService.getSessionQuestions(sessionId));
    }

    // 4. API: Nộp bài, chấm điểm và phạt/thưởng SM-2
    @PostMapping("/{sessionId}/submit")
    public ResponseEntity<DeepDiveDto.SubmitResponse> submitSession(
            @PathVariable UUID sessionId,
            @RequestBody DeepDiveDto.SubmitRequest request) {
        UUID userId = SecurityUtils.getCurrentUser().getId();
        return ResponseEntity.ok(deepDiveService.gradeAndSubmitSession(userId, sessionId, request));
    }
}