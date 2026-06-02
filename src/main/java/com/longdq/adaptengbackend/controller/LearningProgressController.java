package com.longdq.adaptengbackend.controller;


import com.longdq.adaptengbackend.dto.UpdateProgressRequest;
import com.longdq.adaptengbackend.entity.UserLearningProgress;
import com.longdq.adaptengbackend.repository.UserLearningProgressRepository;
import com.longdq.adaptengbackend.service.SpacedRepetitionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("api/v1/learning")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class LearningProgressController {
private final SpacedRepetitionService spacedRepetitionService;
private final UserLearningProgressRepository progressRepository;
@PostMapping("/progress")
    public ResponseEntity<?> handleUserAnswer(@Valid @RequestBody UpdateProgressRequest request) {
    UserLearningProgress currentProgress = progressRepository.findById(request.getProgressId()).orElse(null);
    if(currentProgress == null) {
        return ResponseEntity.notFound().build();
    }
    UserLearningProgress updatedProgress = spacedRepetitionService.updateProgress(currentProgress, request.getIsCorrect());

    return ResponseEntity.ok(updatedProgress);
}
}
