package com.longdq.adaptengbackend.modules.premium.service;

import com.longdq.adaptengbackend.modules.user.entity.User;
import com.longdq.adaptengbackend.modules.premium.entity.VipDailyEntertainment;
import com.longdq.adaptengbackend.modules.premium.entity.VipSavedWord;
import com.longdq.adaptengbackend.common.enums.VipSavedWordStatus;
import com.longdq.adaptengbackend.common.exception.ResourceNotFoundException;
import com.longdq.adaptengbackend.modules.premium.repository.VipDailyEntertainmentRepository;
import com.longdq.adaptengbackend.modules.premium.repository.VipSavedWordRepository;
import com.longdq.adaptengbackend.common.security.PremiumCheckUtil;
import com.longdq.adaptengbackend.common.security.SecurityUtils;
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
import com.longdq.adaptengbackend.modules.premium.dto.VipActionResponseDto;
import com.longdq.adaptengbackend.modules.premium.dto.VipEntertainmentEmptyReason;
import com.longdq.adaptengbackend.modules.premium.dto.VipEntertainmentResponseDto;
import com.longdq.adaptengbackend.modules.premium.dto.VipEntertainmentStatus;
import com.longdq.adaptengbackend.modules.premium.dto.VipEntertainmentStatusDto;
import com.longdq.adaptengbackend.modules.premium.dto.VipFomoResponseDto;
import com.longdq.adaptengbackend.modules.premium.dto.VipPendingWordDto;

@Slf4j
@Service
@RequiredArgsConstructor
public class VipService {

    private final VipSavedWordRepository vipSavedWordRepository;
    private final VipDailyEntertainmentRepository vipDailyEntertainmentRepository;
    private final PremiumCheckUtil premiumCheckUtil;

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
     * Lấy nội dung giải trí VIP hôm nay (Tarot & Story).
     * Phân biệt rõ 3 trạng thái để FE hiển thị đúng bản chất:
     * HAS_STORY (có đề dở, kèm ngày của đề) / DONE_TODAY (hôm nay đã xong) / EMPTY (chưa có đề + lý do).
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
                    .status(VipEntertainmentStatus.HAS_STORY)
                    .build();
        }

        // Không còn đề dở: hôm nay đã làm xong hay chưa có đề nào?
        boolean doneToday = vipDailyEntertainmentRepository
                .existsByUserIdAndIsCompletedAndEntertainmentDate(user.getId(), true, LocalDate.now());
        if (doneToday) {
            return VipEntertainmentResponseDto.builder()
                    .contentJson(null)
                    .isCompleted(true)
                    .status(VipEntertainmentStatus.DONE_TODAY)
                    .build();
        }

        return VipEntertainmentResponseDto.builder()
                .contentJson(null)
                .isCompleted(true)
                .status(VipEntertainmentStatus.EMPTY)
                .emptyReason(resolveEmptyReason(user.getId()))
                .build();
    }

    private VipEntertainmentEmptyReason resolveEmptyReason(UUID userId) {
        if (!premiumCheckUtil.isPremiumUser(userId)) {
            return VipEntertainmentEmptyReason.NOT_VIP;
        }
        long pendingCount = vipSavedWordRepository.countByUserIdAndStatus(userId, VipSavedWordStatus.PENDING);
        if (pendingCount == 0) {
            return VipEntertainmentEmptyReason.NO_PENDING_WORDS;
        }
        return VipEntertainmentEmptyReason.WAITING_JOB;
    }

    /**
     * Bỏ qua đề đang dở (chống kẹt FOMO khi đề cũ hỏng/không muốn làm nữa).
     */
    @Transactional
    public VipActionResponseDto skipStory() {
        User user = SecurityUtils.getCurrentUser();

        VipDailyEntertainment entertainment = vipDailyEntertainmentRepository
                .findFirstByUserIdAndIsCompletedOrderByIdAsc(user.getId(), false)
                .orElseThrow(() -> new ResourceNotFoundException("Không có đề nào đang dở để bỏ qua."));

        entertainment.setIsCompleted(true);
        vipDailyEntertainmentRepository.save(entertainment);

        return VipActionResponseDto.builder()
                .success(true)
                .message("Đã bỏ qua đề cũ. Hãy lưu từ mới để nhận đề tiếp theo sau 2h sáng.")
                .build();
    }

    /**
     * Trạng thái chờ đề để FE tự giải thích vì sao chưa có đề.
     */
    public VipEntertainmentStatusDto getEntertainmentStatus() {
        User user = SecurityUtils.getCurrentUser();

        return VipEntertainmentStatusDto.builder()
                .pendingCount(vipSavedWordRepository.countByUserIdAndStatus(user.getId(), VipSavedWordStatus.PENDING))
                .vip(premiumCheckUtil.isPremiumUser(user.getId()))
                .hasIncompleteStory(vipDailyEntertainmentRepository.existsByUserIdAndIsCompleted(user.getId(), false))
                .nextRunAt("02:00")
                .build();
    }

    /**
     * Hoàn thành cốt truyện -> mở khóa lưu từ cho ngày mới
     */
    @Transactional
    public VipActionResponseDto completeStory() {
        User user = SecurityUtils.getCurrentUser();

        // ĐỔI SANG DÙNG HÀM TÌM KIẾM THEO TRẠNG THÁI CHƯA HOÀN THÀNH (BỎ QUA NGÀY THÁNG)
        VipDailyEntertainment entertainment = vipDailyEntertainmentRepository
                .findFirstByUserIdAndIsCompletedOrderByIdAsc(user.getId(), false)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nội dung giải trí đang làm dở."));

        if (Boolean.TRUE.equals(entertainment.getIsCompleted())) {
            return VipActionResponseDto.builder()
                    .success(true)
                    .message("Cốt truyện đã được hoàn thành trước đó.")
                    .build();
        }

        // Chốt sổ
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