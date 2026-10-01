package com.longdq.adaptengbackend.modules.spacedrepetition.controller;

import com.longdq.adaptengbackend.modules.spacedrepetition.dto.SaveWordRequestDto;
import com.longdq.adaptengbackend.modules.spacedrepetition.dto.UpdateProgressRequest;
import com.longdq.adaptengbackend.modules.spacedrepetition.entity.UserLearningProgress;
import com.longdq.adaptengbackend.common.exception.ResourceNotFoundException;
import com.longdq.adaptengbackend.modules.spacedrepetition.repository.UserLearningProgressRepository;
import com.longdq.adaptengbackend.modules.spacedrepetition.service.SaveWordService;
import com.longdq.adaptengbackend.modules.spacedrepetition.service.SpacedRepetitionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/learning")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class LearningProgressController {

    private final SpacedRepetitionService spacedRepetitionService;
    private final UserLearningProgressRepository progressRepository;
    private final SaveWordService saveWordService;

    @PostMapping("/progress")
    public ResponseEntity<UserLearningProgress> handleUserAnswer(@Valid @RequestBody UpdateProgressRequest request) {
        UserLearningProgress currentProgress = progressRepository.findById(request.getProgressId())
                .orElseThrow(() -> new ResourceNotFoundException("Learning progress not found"));

        UserLearningProgress updatedProgress = spacedRepetitionService.updateProgress(
                currentProgress, request.getIsCorrect());

        return ResponseEntity.ok(updatedProgress);
    }

    @PostMapping("/save-word")
    public ResponseEntity<UserLearningProgress> saveWord(@Valid @RequestBody SaveWordRequestDto request) {
        UserLearningProgress saved = saveWordService.saveWord(request);
        return ResponseEntity.ok(saved);
    }
}
