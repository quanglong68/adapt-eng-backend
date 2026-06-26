package com.longdq.adaptengbackend.service;

import com.longdq.adaptengbackend.dto.*;
import com.longdq.adaptengbackend.entity.User;
import com.longdq.adaptengbackend.entity.VipDailyEntertainment;
import com.longdq.adaptengbackend.entity.VipSavedWord;
import com.longdq.adaptengbackend.enums.VipSavedWordStatus;
import com.longdq.adaptengbackend.exception.ResourceNotFoundException;
import com.longdq.adaptengbackend.repository.VipDailyEntertainmentRepository;
import com.longdq.adaptengbackend.repository.VipSavedWordRepository;
import com.longdq.adaptengbackend.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VipService {

    private final VipSavedWordRepository vipSavedWordRepository;
    private final VipDailyEntertainmentRepository vipDailyEntertainmentRepository;

    private static final int MAX_SAVED_WORDS = 10;

    /**
     * Lưu từ vào giỏ từ VIP (Word Cart)
     */
    @Transactional
    public VipActionResponseDto saveWord(String word) {
        User user = SecurityUtils.getCurrentUser();

        // Check FOMO: user có BẤT KỲ truyện nào isCompleted = false không
        boolean hasIncompleteStory = vipDailyEntertainmentRepository
                .existsByUserIdAndIsCompleted(user.getId(), false);

        if (hasIncompleteStory) {
            return VipActionResponseDto.builder()
                    .success(false)
                    .message("Hãy giải mã cốt truyện hôm qua để mở khóa giỏ từ vựng hôm nay nhé!")
                    .locked(true)
                    .build();
        }

        // Check max limit
        long currentCount = vipSavedWordRepository.countByUserIdAndStatus(user.getId(), VipSavedWordStatus.PENDING);
        if (currentCount >= MAX_SAVED_WORDS) {
            return VipActionResponseDto.builder()
                    .success(false)
                    .message("Bạn đã đạt giới hạn " + MAX_SAVED_WORDS + " từ. Hãy chờ xử lý vào 2h sáng mai.")
                    .locked(false)
                    .currentCount((int) currentCount)
                    .maxCount(MAX_SAVED_WORDS)
                    .build();
        }

        // Check duplicate
        Optional<VipSavedWord> existing = vipSavedWordRepository.findByUserIdAndWordAndStatus(
                user.getId(), word.trim().toLowerCase(), VipSavedWordStatus.PENDING);
        if (existing.isPresent()) {
            return VipActionResponseDto.builder()
                    .success(false)
                    .message("Từ này đã có trong giỏ từ.")
                    .locked(false)
                    .currentCount((int) currentCount)
                    .maxCount(MAX_SAVED_WORDS)
                    .build();
        }

        VipSavedWord savedWord = new VipSavedWord();
        savedWord.setUserId(user.getId());
        savedWord.setWord(word.trim().toLowerCase());
        savedWord.setStatus(VipSavedWordStatus.PENDING);
        savedWord.setCreatedAt(LocalDateTime.now());
        vipSavedWordRepository.save(savedWord);

        return VipActionResponseDto.builder()
                .success(true)
                .message("Đã lưu từ \"" + word + "\" vào giỏ từ VIP.")
                .currentCount((int) currentCount + 1)
                .maxCount(MAX_SAVED_WORDS)
                .build();
    }

    /**
     * Xóa từ khỏi giỏ từ VIP
     */
    @Transactional
    public VipActionResponseDto removeWord(String word) {
        User user = SecurityUtils.getCurrentUser();
        vipSavedWordRepository.deleteByUserIdAndWordAndStatus(user.getId(), word.trim().toLowerCase(), VipSavedWordStatus.PENDING);
        return VipActionResponseDto.builder()
                .success(true)
                .message("Đã xóa từ khỏi giỏ từ.")
                .build();
    }

    /**
     * Lấy danh sách từ PENDING trong ngày
     */
    public List<VipPendingWordDto> getPendingWords() {
        User user = SecurityUtils.getCurrentUser();
        List<VipSavedWord> words = vipSavedWordRepository.findByUserIdAndStatusOrderByCreatedAtAsc(
                user.getId(), VipSavedWordStatus.PENDING);

        return words.stream()
                .map(w -> VipPendingWordDto.builder()
                        .id(w.getId())
                        .word(w.getWord())
                        .createdAt(w.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Lấy nội dung giải trí VIP hôm nay (Tarot & Story)
     */
    public VipEntertainmentResponseDto getDailyEntertainment() {
        User user = SecurityUtils.getCurrentUser();


        Optional<VipDailyEntertainment> entertainment = vipDailyEntertainmentRepository
                .findFirstByUserIdAndIsCompletedOrderByIdAsc(user.getId(), false);

        if (entertainment.isPresent()) {
            VipDailyEntertainment e = entertainment.get();
            return VipEntertainmentResponseDto.builder()
                    .id(e.getId())
                    .contentJson(e.getContentJson())
                    .isCompleted(e.getIsCompleted())
                    .entertainmentDate(e.getEntertainmentDate())
                    .build();
        } else {
            return VipEntertainmentResponseDto.builder()
                    .contentJson(null)
                    .isCompleted(true)
                    .build();
        }
    }

    /**
     * Hoàn thành cốt truyện -> mở khóa lưu từ cho ngày mới
     */
    @Transactional
    public VipActionResponseDto completeStory() {
        User user = SecurityUtils.getCurrentUser();
        LocalDate today = LocalDate.now();

        VipDailyEntertainment entertainment = vipDailyEntertainmentRepository
                .findByUserIdAndEntertainmentDate(user.getId(), today)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nội dung giải trí hôm nay."));

        if (Boolean.TRUE.equals(entertainment.getIsCompleted())) {
            return VipActionResponseDto.builder()
                    .success(true)
                    .message("Cốt truyện đã được hoàn thành trước đó.")
                    .build();
        }

        entertainment.setIsCompleted(true);
        vipDailyEntertainmentRepository.save(entertainment);

        return VipActionResponseDto.builder()
                .success(true)
                .message("Chúc mừng! Bạn đã hoàn thành cốt truyện hôm nay.")
                .build();
    }

    /**
     * Kiểm tra FOMO: user có BẤT KỲ truyện nào chưa hoàn thành không
     */
    public VipFomoResponseDto checkFomo() {
        User user = SecurityUtils.getCurrentUser();

        boolean hasIncompleteStory = vipDailyEntertainmentRepository
                .existsByUserIdAndIsCompleted(user.getId(), false);

        return VipFomoResponseDto.builder()
                .locked(hasIncompleteStory)
                .message(hasIncompleteStory
                        ? "Hãy giải mã cốt truyện hôm qua để mở khóa giỏ từ vựng hôm nay nhé!"
                        : "Bạn có thể lưu từ mới.")
                .build();
    }
}