package com.longdq.adaptengbackend.service;

import com.longdq.adaptengbackend.dto.*;
import com.longdq.adaptengbackend.entity.*;
import com.longdq.adaptengbackend.enums.Purpose;
import com.longdq.adaptengbackend.enums.ToeicPart;
import com.longdq.adaptengbackend.enums.Level;
import com.longdq.adaptengbackend.repository.*;
import com.longdq.adaptengbackend.util.DailyReviewProgressHelper;
import com.longdq.adaptengbackend.util.KnowledgeDisplayNameUtil;
import com.longdq.adaptengbackend.util.ProgressCacheKeyUtil;
import com.longdq.adaptengbackend.util.QuestionReviewBuilder;
import com.longdq.adaptengbackend.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ToeicPracticeService {

    private final UserLearningProgressRepository progressRepository;
    private final QuestionRepository questionRepository;
    private final PassageRepository passageRepository;
    private final UserQuestionHistoryRepository userQuestionHistoryRepository;
    private final SpacedRepetitionService spacedRepetitionService;
    private final UserRepository userRepository;
    private final DataSyncService dataSyncService;

    // =========================================================================
    // 1. LUỒNG LẮP RÁP ĐỀ THI ÔN TẬP HÀNG NGÀY (MAY ĐO THEO KHUÔN ĐỀ 50 CÂU)
    // =========================================================================
    public List<ToeicPassageResponseDto> generateDailyToeicTest() {
        User user = SecurityUtils.getCurrentUser();
        String userLevel = user.getCurrentLevel().name();

        // Khởi tạo các thùng chứa ID để tránh trùng lặp câu hỏi và đoạn văn trong đề
        List<Long> pickedQuestionIds = new ArrayList<>(List.of(-1L));
        List<Long> pickedPassageIds = new ArrayList<>(List.of(-1L));
        List<Question> finalTestQuestions = new ArrayList<>();

        // Quét tìm tất cả tiến độ nợ ôn tập bám sát thuật toán SM-2 của User
        List<UserLearningProgress> dueItems = progressRepository
                .findByUserIdAndNextReviewDateLessThanEqualOrderByIntervalDaysAscNextReviewDateAsc(
                        user.getId(), LocalDateTime.now()
                );

        int part5Count = 0;
        int part6Count = 0;
        int part7SingleCount = 0;
        int part7MultiCount = 0;

        // BƯỚC 1: TRẢ NỢ THUẬT TOÁN SM-2 (ƯU TIÊN: CÂU MỚI TOANH -> XOAY VÒNG CÂU CŨ NHẤT LRU)
        for (UserLearningProgress item : dueItems) {
            ToeicPart part = item.getToeicPart();
            if (part == null) continue;

            UUID kId = item.getKnowledgeItem() != null ? item.getKnowledgeItem().getId() : null;
            String targetWord = item.getTargetWord();

            // Xử lý Part 5 câu đơn lẻ (Tối đa 15 câu)
            if (part == ToeicPart.PART_5 && part5Count < 15) {
                Optional<Question> qOpt = questionRepository.findNewPart5Question(kId, targetWord, user.getId(), pickedQuestionIds);
                if (qOpt.isEmpty()) {
                    qOpt = questionRepository.findLruPart5Question(kId, targetWord, user.getId(), pickedQuestionIds);
                }

                if (qOpt.isPresent()) {
                    finalTestQuestions.add(qOpt.get());
                    pickedQuestionIds.add(qOpt.get().getId());
                    part5Count++;
                }
            }
            // Xử lý các Part có chứa đoạn văn đọc hiểu
            else if (part == ToeicPart.PART_6 && part6Count < 2) {
                part6Count += processPassageReview(ToeicPart.PART_6, kId, targetWord, userLevel, user.getId(), pickedQuestionIds, finalTestQuestions, pickedPassageIds, item);
            } else if (part == ToeicPart.PART_7_SINGLE && part7SingleCount < 3) {
                part7SingleCount += processPassageReview(ToeicPart.PART_7_SINGLE, kId, targetWord, userLevel, user.getId(), pickedQuestionIds, finalTestQuestions, pickedPassageIds, item);
            } else if (part == ToeicPart.PART_7_MULTIPLE && part7MultiCount < 3) {
                part7MultiCount += processPassageReview(ToeicPart.PART_7_MULTIPLE, kId, targetWord, userLevel, user.getId(), pickedQuestionIds, finalTestQuestions, pickedPassageIds, item);
            }
        }

        // BƯỚC 2: CƠ CHẾ LẤP ĐẦY (FILL GAP) ĐẢM BẢO ĐỦ SỐ LƯỢNG KHI THIẾU ĐẠN NỢ
        if (part5Count < 15) {
            List<Question> fillP5 = questionRepository.findUnansweredPart5ToFill(userLevel, user.getId(), pickedQuestionIds, 15 - part5Count);
            finalTestQuestions.addAll(fillP5);
        }

        part6Count += fillMissingPassages(ToeicPart.PART_6, user, 2 - part6Count, pickedPassageIds, pickedQuestionIds, finalTestQuestions);
        part7SingleCount += fillMissingPassages(ToeicPart.PART_7_SINGLE, user, 3 - part7SingleCount, pickedPassageIds, pickedQuestionIds, finalTestQuestions);
        part7MultiCount += fillMissingPassages(ToeicPart.PART_7_MULTIPLE, user, 3 - part7MultiCount, pickedPassageIds, pickedQuestionIds, finalTestQuestions);

        // BƯỚC 3: ĐÓNG GÓI CHUYỂN ĐỔI SANG CẤU TRÚC PHÂN TẦNG BLOCK CHA - CON
        return packQuestionsIntoPassageBlocks(finalTestQuestions);
    }

    // Helper xử lý nhặt đoạn văn nợ ôn tập
    private int processPassageReview(ToeicPart part, UUID kId, String targetWord, String level, UUID userId, List<Long> pickedQuestionIds, List<Question> finalTestQuestions, List<Long> pickedPassageIds, UserLearningProgress item) {
        // 1. Thử tìm bài đọc mới chứa từ nợ
        Optional<Question> targetQOpt = questionRepository.findNewTargetedPassageQuestion(part.name(), kId, targetWord, level, userId, pickedQuestionIds);

        // 2. Nếu hết bài mới -> Xoay vòng tìm bài cũ nhất (LRU) từng làm để tái chế bộ nhớ
        if (targetQOpt.isEmpty()) {
            targetQOpt = questionRepository.findLruTargetedPassageQuestion(part.name(), kId, targetWord, level, userId, pickedQuestionIds);
        }

        // 3. Fallback cấp bách: Nếu DB trống rỗng hoàn toàn, ép AI sinh lập tức
        if (targetQOpt.isEmpty()) {
            try {
                dataSyncService.generateAndSaveToeicPassage(Level.valueOf(level), part, item.getKnowledgeItem(), targetWord, Purpose.PRACTICE);
                targetQOpt = questionRepository.findNewTargetedPassageQuestion(part.name(), kId, targetWord, level, userId, pickedQuestionIds);
            } catch (Exception e) {
                log.error("Failed to generate emergency SM-2 passage: {}", e.getMessage(), e);
            }
        }

        if (targetQOpt.isPresent() && targetQOpt.get().getPassage() != null) {
            Passage passage = targetQOpt.get().getPassage();
            if (!pickedPassageIds.contains(passage.getId())) {
                pickedPassageIds.add(passage.getId());
                // Hốt trọn gói toàn bộ câu hỏi vệ tinh xoay quanh đoạn văn cha này vào đề
                for (Question q : passage.getQuestions()) {
                    finalTestQuestions.add(q);
                    pickedQuestionIds.add(q.getId());
                }
                return 1;
            }
        }
        return 0;
    }

    // Helper xử lý lấp đầy cấu trúc đề - Khống chế nghẽn request AI và có phương án cứu hộ bài cũ
    private int fillMissingPassages(ToeicPart part, User user, int amountNeeded, List<Long> pickedPassageIds, List<Long> pickedQuestionIds, List<Question> finalTestQuestions) {
        if (amountNeeded <= 0) return 0;
        int filled = 0;

        // Bước A: Quét DB tìm các đoạn văn ngẫu nhiên người dùng CHƯA TỪNG ĐỌC bao giờ
        List<Passage> randomPassages = passageRepository.findUnansweredPassagesToFill(part.name(), user.getId(), pickedPassageIds, amountNeeded);
        for (Passage p : randomPassages) {
            pickedPassageIds.add(p.getId());
            finalTestQuestions.addAll(p.getQuestions());
            p.getQuestions().forEach(q -> pickedQuestionIds.add(q.getId()));
            filled++;
        }

        // Bước B: Nếu DB cạn bài mới -> Ép AI sinh, khống chế tối đa 2 lần thử (Tránh DDOS Server khi lỗi mạng)
        int aiRetries = 0;
        int MAX_AI_RETRIES = 2;

        while (filled < amountNeeded && aiRetries < MAX_AI_RETRIES) {
            try {
                aiRetries++;
                log.warn("Empty random question pool, invoking real-time AI generation (attempt {})", aiRetries);
                dataSyncService.generateAndSaveToeicPassage(user.getCurrentLevel(), part, null, null, Purpose.PRACTICE);

                List<Passage> freshPassages = passageRepository.findUnansweredPassagesToFill(part.name(), user.getId(), pickedPassageIds, 1);
                if (!freshPassages.isEmpty()) {
                    Passage p = freshPassages.get(0);
                    pickedPassageIds.add(p.getId());
                    finalTestQuestions.addAll(p.getQuestions());
                    p.getQuestions().forEach(q -> pickedQuestionIds.add(q.getId()));
                    filled++;
                }
            } catch (Exception e) {
                log.error("Gemini API error while filling practice test: {}", e.getMessage(), e);
            }
        }

        // Bước C: PHƯƠNG ÁN PHÒNG NGỰ CUỐI CÙNG (ULTIMATE FALLBACK)
        // Nếu AI sập hoặc cạn quota, hốt những đoạn văn cũ nhất đã từng làm ra đắp vào để cứu luồng chạy thời gian thực
        if (filled < amountNeeded) {
            int remaining = amountNeeded - filled;
            log.warn("AI unavailable, activating fallback: fetching {} oldest passage blocks", remaining);

            List<Passage> oldPassages = passageRepository.findOldestAnsweredPassagesToFill(part.name(), user.getId(), pickedPassageIds, remaining);
            for (Passage p : oldPassages) {
                pickedPassageIds.add(p.getId());
                finalTestQuestions.addAll(p.getQuestions());
                p.getQuestions().forEach(q -> pickedQuestionIds.add(q.getId()));
                filled++;
            }
        }

        return filled;
    }

    // Helper phân nhóm mảng phẳng thành các Block cấu trúc cây trả về Frontend (Sử dụng DTO độc lập)
    private List<ToeicPassageResponseDto> packQuestionsIntoPassageBlocks(List<Question> flatQuestions) {
        List<ToeicPassageResponseDto> packedBlocks = new ArrayList<>();

        // Tách và gom riêng Part 5 (Câu lẻ không có đoạn văn)
        List<Question> part5s = flatQuestions.stream().filter(q -> q.getToeicPart() == ToeicPart.PART_5).collect(Collectors.toList());
        if (!part5s.isEmpty()) {
            List<ToeicQuestionResponseDto> dtos = part5s.stream().map(q ->
                    new ToeicQuestionResponseDto(q.getId(), q.getContent(), q.getOptions(), q.getQuestionType())
            ).collect(Collectors.toList());
            packedBlocks.add(new ToeicPassageResponseDto(null, null, ToeicPart.PART_5, dtos));
        }

        // Gom các bài đọc Part 6, 7 dựa trên liên kết thực thể Passage cha
        Map<Long, Passage> uniquePassages = new LinkedHashMap<>(); // Bảo toàn thứ tự bốc đề
        for (Question q : flatQuestions) {
            if (q.getPassage() != null) {
                uniquePassages.putIfAbsent(q.getPassage().getId(), q.getPassage());
            }
        }

        for (Passage p : uniquePassages.values()) {
            List<ToeicQuestionResponseDto> dtos = p.getQuestions().stream().map(q ->
                    new ToeicQuestionResponseDto(q.getId(), q.getContent(), q.getOptions(), q.getQuestionType())
            ).collect(Collectors.toList());

            ToeicPart partLabel = p.getQuestions().isEmpty() ? ToeicPart.PART_6 : p.getQuestions().get(0).getToeicPart();
            packedBlocks.add(new ToeicPassageResponseDto(p.getId(), p.getContent(), partLabel, dtos));
        }

        return packedBlocks;
    }

    // =========================================================================
    // 2. CHẤM ĐIỂM BÀI LÀM VÀ VÁ TRIỆT ĐỂ LỖI NHẢY BẬC KÉP SM-2 (SUBMIT)
    // =========================================================================
    @Transactional
    public DailyReviewResultResponseDto submitDailyToeicReview(DailyReviewSubmissionRequestDto request) {
        User user = SecurityUtils.getCurrentUser();
        LocalDateTime now = LocalDateTime.now();

        List<Long> questionIds = request.getAnswers().stream()
                .map(UserAnswerDto::getQuestionId)
                .collect(Collectors.toList());
        Map<Long, Question> questionMap = questionRepository.findAllById(questionIds).stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        List<UserQuestionHistory> historiesToSave = new ArrayList<>();
        List<QuestionReviewDto> results = new ArrayList<>();
        Map<String, UserLearningProgress> progressCache = new HashMap<>();
        Set<String> processedSm2KeysThisSession = new HashSet<>();

        int correctCount = 0;
        int totalEarnedXp = 0;

        for (UserAnswerDto answer : request.getAnswers()) {
            Question question = questionMap.get(answer.getQuestionId());
            if (question == null) {
                continue;
            }

            boolean isCorrect = question.getCorrectAnswer().equalsIgnoreCase(answer.getSelectedAnswer());
            if (isCorrect) {
                correctCount++;
            }
            totalEarnedXp += isCorrect ? 10 : 2;

            UserQuestionHistory history = new UserQuestionHistory();
            history.setUserId(user.getId());
            history.setQuestionId(question.getId());
            history.setCorrect(isCorrect);
            history.setAnsweredAt(now);
            historiesToSave.add(history);

            UUID knowledgeId = question.getKnowledgeItem() != null ? question.getKnowledgeItem().getId() : null;
            String targetWord = question.getTargetWord();

            if (knowledgeId != null || targetWord != null) {
                String cacheKey = ProgressCacheKeyUtil.buildKey(knowledgeId, targetWord);

                if (!processedSm2KeysThisSession.contains(cacheKey)) {
                    UserLearningProgress progress = DailyReviewProgressHelper.resolveOrCreateProgress(
                            user, question, progressCache, progressRepository, question.getToeicPart());
                    spacedRepetitionService.updateProgress(progress, isCorrect);
                    processedSm2KeysThisSession.add(cacheKey);
                }
            }

            results.add(QuestionReviewBuilder.build(
                    question,
                    answer.getSelectedAnswer(),
                    isCorrect,
                    KnowledgeDisplayNameUtil.forDailyReview(question)));
        }

        // Đẩy hàng loạt dữ liệu xuống DB thông qua duy nhất 1 câu query tích lũy
        userQuestionHistoryRepository.saveAll(historiesToSave);
        progressRepository.saveAll(progressCache.values());

        user.setTotalXp(user.getTotalXp() + totalEarnedXp);
        userRepository.save(user);

        return new DailyReviewResultResponseDto(request.getAnswers().size(), correctCount, results);
    }
}