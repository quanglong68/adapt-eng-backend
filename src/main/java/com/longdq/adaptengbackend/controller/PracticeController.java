//package com.longdq.adaptengbackend.controller;
//
//import com.longdq.adaptengbackend.dto.DailyReviewResultResponseDto;
//import com.longdq.adaptengbackend.dto.DailyReviewSubmissionRequestDto;
//import com.longdq.adaptengbackend.dto.QuestionResponseDto;
//import com.longdq.adaptengbackend.service.PracticeService;
//import lombok.RequiredArgsConstructor;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.List;
//
//@RestController
//@RequestMapping("/api/v1/practice")
//@RequiredArgsConstructor
//@CrossOrigin(origins = "*")
//public class PracticeController {
//    private final PracticeService practiceService;
//    @GetMapping("/daily")
//    public ResponseEntity<List<QuestionResponseDto>> getDailyReviewTest() {
//        return ResponseEntity.ok(practiceService.generateDailyReviewTest());
//    }
//
//    @PostMapping("/submit")
//    public ResponseEntity<DailyReviewResultResponseDto> submitDailyReview(@RequestBody DailyReviewSubmissionRequestDto request) {
//        return ResponseEntity.ok(practiceService.submitDailyReview(request));
//    }
//}