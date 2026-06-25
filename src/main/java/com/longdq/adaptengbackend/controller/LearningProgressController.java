package com.longdq.adaptengbackend.controller;

import com.longdq.adaptengbackend.dto.SaveWordRequestDto;
import com.longdq.adaptengbackend.dto.UpdateProgressRequest;
import com.longdq.adaptengbackend.entity.UserLearningProgress;
import com.longdq.adaptengbackend.exception.ResourceNotFoundException;
import com.longdq.adaptengbackend.repository.UserLearningProgressRepository;
import com.longdq.adaptengbackend.service.SaveWordService;
import com.longdq.adaptengbackend.service.SpacedRepetitionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/learning")
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
