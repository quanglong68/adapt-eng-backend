package com.longdq.adaptengbackend.modules.writing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.longdq.adaptengbackend.common.exception.ForbiddenException;
import com.longdq.adaptengbackend.common.exception.QuotaExceededException;
import com.longdq.adaptengbackend.common.exception.ResourceNotFoundException;
import com.longdq.adaptengbackend.common.exception.ValidationException;
import com.longdq.adaptengbackend.common.ai.AIPromptTemplates;
import com.longdq.adaptengbackend.common.util.DailyReviewProgressHelper;
import com.longdq.adaptengbackend.common.security.PremiumCheckUtil;
import com.longdq.adaptengbackend.common.util.ProgressCacheKeyUtil;
import com.longdq.adaptengbackend.common.util.RetryExecutor;
import com.longdq.adaptengbackend.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import com.longdq.adaptengbackend.common.ai.AIService;
import com.longdq.adaptengbackend.common.dto.DailyReviewSubmissionRequestDto;
import com.longdq.adaptengbackend.common.dto.QuestionReviewDto;
import com.longdq.adaptengbackend.common.dto.SaveDraftRequestDto;
import com.longdq.adaptengbackend.common.dto.UserAnswerDto;
import com.longdq.adaptengbackend.common.enums.KnowledgeType;
import com.longdq.adaptengbackend.common.enums.Level;
import com.longdq.adaptengbackend.common.enums.Purpose;
import com.longdq.adaptengbackend.common.enums.TestRecordStatus;
import com.longdq.adaptengbackend.common.enums.ToeicPart;
import com.longdq.adaptengbackend.common.exception.BusinessException;
import com.longdq.adaptengbackend.common.security.PlacementTestInterceptor;
import com.longdq.adaptengbackend.modules.progress.repository.AppConfigRepository;
import com.longdq.adaptengbackend.modules.spacedrepetition.entity.KnowledgeItem;
import com.longdq.adaptengbackend.modules.spacedrepetition.entity.UserLearningProgress;
import com.longdq.adaptengbackend.modules.spacedrepetition.repository.KnowledgeItemRepository;
import com.longdq.adaptengbackend.modules.spacedrepetition.repository.UserLearningProgressRepository;
import com.longdq.adaptengbackend.modules.spacedrepetition.service.SpacedRepetitionService;
import com.longdq.adaptengbackend.modules.toeic.service.ToeicPracticeService;
import com.longdq.adaptengbackend.modules.user.entity.User;
import com.longdq.adaptengbackend.modules.user.repository.UserRepository;
import com.longdq.adaptengbackend.modules.writing.dto.WritingAiGradingResponseDto;
import com.longdq.adaptengbackend.modules.writing.dto.WritingPracticeHistoryDto;
import com.longdq.adaptengbackend.modules.writing.dto.WritingPracticeResultResponseDto;
import com.longdq.adaptengbackend.modules.writing.dto.WritingPracticeSessionDto;
import com.longdq.adaptengbackend.modules.writing.dto.WritingQuestionDto;
import com.longdq.adaptengbackend.modules.writing.entity.UserWritingQuestionHistory;
import com.longdq.adaptengbackend.modules.writing.entity.WritingQuestion;
import com.longdq.adaptengbackend.modules.writing.entity.WritingTestRecord;
import com.longdq.adaptengbackend.modules.writing.repository.UserWritingQuestionHistoryRepository;
import com.longdq.adaptengbackend.modules.writing.repository.WritingQuestionRepository;
import com.longdq.adaptengbackend.modules.writing.repository.WritingTestRecordRepository;

/**
 * LUYỆN TẬP WRITING PART 1 HÀNG NGÀY.
 *
 * Mirror toàn bộ cơ chế của ToeicPracticeService (Reading) nhưng chạy trên
 * bảng writing_questions + writing_test_records (testType = "DAILY_TEST").
 * Quota đếm RIÊNG, không dùng chung với Reading.
 *
 * Điểm khác biệt cốt lõi so với Reading:
 * - Đề được chấm bằng AI (Gemini Vision) theo thang 0-3 mỗi câu, không có đáp án đúng/sai.
 * - Câu bốc từ SM-2 sẽ bị LỘ ngữ pháp bắt buộc (để luyện đúng điểm yếu, dùng sai sẽ bị phạt 0 điểm).
 * - Câu bốc ngẫu nhiên sẽ bị ÉM ngữ pháp (requiredGrammar = null) để AI chấm tự do.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WritingPracticeService {

    private final WritingQuestionRepository questionRepository;
    private final WritingTestRecordRepository recordRepository;
    private final UserWritingQuestionHistoryRepository writingHistoryRepository;
    private final UserLearningProgressRepository progressRepository;
    private final KnowledgeItemRepository knowledgeItemRepository;
    private final UserRepository userRepository;
    private final SpacedRepetitionService spacedRepetitionService;
    private final AIService aiService;
    private final PremiumCheckUtil premiumCheckUtil;
    private final AppConfigRepository appConfigRepository;
    private final ObjectMapper objectMapper;

    // Loại bài luyện tập hàng ngày (khác với "PLACEMENT_TEST" của WritingTestService)
    private static final String TEST_TYPE_DAILY = "DAILY_TEST";

    // Số câu trong một đề luyện tập (giống placement test)
    private static final int DAILY_QUESTION_COUNT = 3;

    // Điểm tối đa mỗi câu do AI chấm
    private static final int MAX_SCORE_PER_QUESTION = 3;

    // Quota: Free 1 đề/ngày, VIP tối đa 3 đề/ngày (giống Reading)
    private static final long FREE_DAILY_LIMIT = 1;
    private static final long VIP_DAILY_LIMIT = 3;

    private static final int MAX_GRADING_RETRIES = 3;
    private static final long GRADING_RETRY_DELAY_MS = 10000;
    private static final long GRADING_RETRY_BACKOFF_MS = 2000;

    // =================================================================================================
    // PHASE 1: LẤY / TẠO PHIÊN LUYỆN TẬP
    // =================================================================================================

    @Transactional
    public WritingPracticeSessionDto getOrCreateDailyPractice() {
        User user = SecurityUtils.getCurrentUser();
        LocalDate today = LocalDate.now();

        // A. DỌN DẸP - HẾT THỜI GIAN: Hủy các đề làm dở của hôm trở về trước
        List<WritingTestRecord> oldPendingRecords = recordRepository
                .findByUserIdAndTestTypeAndStatusAndTestDateLessThan(
                        user.getId(), TEST_TYPE_DAILY, TestRecordStatus.IN_PROGRESS, today);
        if (!oldPendingRecords.isEmpty()) {
            for (WritingTestRecord oldRecord : oldPendingRecords) {
                oldRecord.setStatus(TestRecordStatus.EXPIRED);
                oldRecord.setUpdatedAt(LocalDateTime.now());
            }
            recordRepository.saveAll(oldPendingRecords);
        }

        // B. TÌM ĐỀ ĐANG LÀM DỞ CỦA HÔM NAY
        Optional<WritingTestRecord> pendingRecordOpt = recordRepository
                .findFirstByUserIdAndTestTypeAndTestDateAndStatus(
                        user.getId(), TEST_TYPE_DAILY, today, TestRecordStatus.IN_PROGRESS);

        WritingTestRecord todayRecord;

        if (pendingRecordOpt.isPresent()) {
            // Đã có đề → trả về nguyên để user làm tiếp
            todayRecord = pendingRecordOpt.get();
        } else {
            // =======================================================
            // C. CHƯA CÓ ĐỀ -> KIỂM TRA QUOTA -> TẠO ĐỀ MỚI
            // =======================================================
            checkDailyQuota(user.getId(), today);

            // Đã đủ điều kiện QUOTA -> bốc câu hỏi theo SM-2
            List<WritingQuestionDto> questionDtos = buildDailyPracticeQuestions(user);

            if (questionDtos.size() < DAILY_QUESTION_COUNT) {
                log.warn("Hệ thống chỉ bốc được {}/{} câu Writing cho user {} ở level {}",
                        questionDtos.size(), DAILY_QUESTION_COUNT, user.getId(), user.getWritingCurrentLevel());
                throw new ValidationException(
                        "Hệ thống chưa đủ câu hỏi Writing Part 1 cho trình độ của bạn. Vui lòng thử lại sau!");
            }

            todayRecord = new WritingTestRecord();
            todayRecord.setUserId(user.getId());
            todayRecord.setTestType(TEST_TYPE_DAILY);
            todayRecord.setTestDate(today);
            todayRecord.setStatus(TestRecordStatus.IN_PROGRESS);
            todayRecord.setLevel(user.getWritingCurrentLevel());
            todayRecord.setTotalQuestions(questionDtos.size());

            try {
                todayRecord.setQuestionsJson(objectMapper.writeValueAsString(questionDtos));
            } catch (JsonProcessingException e) {
                log.error("Lỗi serialize JSON đề luyện tập Writing", e);
                throw new RuntimeException("Lỗi hệ thống khi tạo đề luyện tập. Vui lòng thử lại sau!");
            }

            todayRecord = recordRepository.save(todayRecord);
            log.info("Đã tạo đề luyện tập Writing cho user: {} (level {})",
                    user.getId(), user.getWritingCurrentLevel());
        }

        return buildSessionDto(todayRecord);
    }

    /**
     * Quota luyện tập Writing: Free 1 đề/ngày, VIP tối đa 3 đề/ngày.
     * Đếm riêng trên bảng writing_test_records nên không ăn vào quota của Reading.
     */
    private void checkDailyQuota(UUID userId, LocalDate today) {
        long completedCount = recordRepository.countByUserIdAndTestTypeAndTestDateAndStatus(
                userId, TEST_TYPE_DAILY, today, TestRecordStatus.COMPLETED);

        if (completedCount >= FREE_DAILY_LIMIT) {
            if (!premiumCheckUtil.isPremiumUser(userId)) {
                // Ném lỗi BusinessException để FE bắt và hiển thị UI mua VIP
                throw new ForbiddenException("REQUIRE_VIP");
            }
            if (completedCount >= VIP_DAILY_LIMIT) {
                // VIP cũng chỉ được làm 3 đề/ngày
                throw new QuotaExceededException("MAX_LIMIT_REACHED");
            }
        }
    }

    /**
     * BỐC ĐỀ THEO THỨ TỰ ƯU TIÊN SM-2.
     * 1. Ưu tiên bốc từ các mục SM-2 đã đến hạn ôn tập (câu chưa từng làm -> câu làm lâu nhất)
     * 2. Thiếu thì bốc random trong đúng level
     * 3. Vẫn thiếu (kho câu mỏng) thì fallback bốc random bỏ qua lịch sử
     *
     * Câu lấy từ SM-2 sẽ LỘ requiredGrammar (bắt buộc dùng đúng ngữ pháp đó).
     * Câu bốc random sẽ ÉM requiredGrammar (= null) để AI chấm tự do.
     */
    private List<WritingQuestionDto> buildDailyPracticeQuestions(User user) {
        Level level = user.getWritingCurrentLevel();
        // PlacementTestInterceptor chỉ chặn khi CẢ HAI level đều null,
        // nên user chỉ làm placement test Reading vẫn có thể gọi vào đây.
        if (level == null) {
            throw new ForbiddenException("REQUIRE_WRITING_PLACEMENT_TEST");
        }

        // Sentinel -1L để câu lệnh "NOT IN" không bao giờ nhận danh sách rỗng
        List<Long> pickedQuestionIds = new ArrayList<>(List.of(-1L));
        List<WritingQuestionDto> result = new ArrayList<>();

        // ---------- 1. ƯU TIÊN SM-2 ----------
        List<UserLearningProgress> dueItems = progressRepository
                .findByUserIdAndNextReviewDateLessThanEqualOrderByIntervalDaysAscNextReviewDateAsc(
                        user.getId(), LocalDateTime.now());

        for (UserLearningProgress item : dueItems) {
            if (result.size() >= DAILY_QUESTION_COUNT) break;
            if (item.getToeicPart() != ToeicPart.WRITING_PART_1) continue;

            UUID kId = item.getKnowledgeItem() != null ? item.getKnowledgeItem().getId() : null;

            Optional<WritingQuestion> qOpt = questionRepository
                    .findNewWritingQuestion(kId, user.getId(), pickedQuestionIds);
            if (qOpt.isEmpty()) {
                qOpt = questionRepository.findLruWritingQuestion(kId, user.getId(), pickedQuestionIds);
            }

            if (qOpt.isPresent()) {
                WritingQuestion q = qOpt.get();
                // LỘ ngữ pháp: để AI ép thí sinh dùng đúng điểm yếu đang ôn
                String requiredGrammar = item.getKnowledgeItem() != null
                        ? item.getKnowledgeItem().getKnowledgeName()
                        : null;
                result.add(convertToDto(q, requiredGrammar));
                pickedQuestionIds.add(q.getId());
            }
        }

        // ---------- 2. THIẾU THÌ BỐC RANDOM ----------
        int missing = DAILY_QUESTION_COUNT - result.size();
        if (missing > 0) {
            List<WritingQuestion> fillQuestions = questionRepository.findUnansweredWritingToFill(
                    level.name(), user.getId(), pickedQuestionIds, missing);
            for (WritingQuestion q : fillQuestions) {
                if (result.size() >= DAILY_QUESTION_COUNT) break;
                // ÉM ngữ pháp: null để AI chấm tự do, không ép cấu trúc
                result.add(convertToDto(q, null));
                pickedQuestionIds.add(q.getId());
            }
        }

        // ---------- 3. VẪN THIẾU -> FALLBACK KHI KHO CÂU MỎNG ----------
        missing = DAILY_QUESTION_COUNT - result.size();
        if (missing > 0) {
            log.warn("Bổ sung {} câu Writing bằng cách bốc ngẫu nhiên không lọc lịch sử (kho câu mỏng)", missing);
            List<WritingQuestion> fallbackQuestions = questionRepository.findRandomByPartAndLevel(
                    ToeicPart.WRITING_PART_1.name(), level.name(), missing);
            for (WritingQuestion q : fallbackQuestions) {
                if (result.size() >= DAILY_QUESTION_COUNT) break;
                if (pickedQuestionIds.contains(q.getId())) continue;
                result.add(convertToDto(q, null));
                pickedQuestionIds.add(q.getId());
            }
        }

        return result;
    }

    // =================================================================================================
    // PHASE 2: LƯU NHÁP VÀ NỘP BÀI
    // =================================================================================================

    @Transactional
    public void saveDraft(SaveDraftRequestDto request) {
        User user = SecurityUtils.getCurrentUser();
        LocalDate today = LocalDate.now();

        // Tìm đề đang làm hôm nay (nếu có)
        WritingTestRecord record = recordRepository
                .findFirstByUserIdAndTestTypeAndTestDateAndStatus(
                        user.getId(), TEST_TYPE_DAILY, today, TestRecordStatus.IN_PROGRESS)
                .orElse(null);
        if (record == null) {
            return;
        }
        if (request == null || request.getAnswers() == null) {
            log.warn("Nhận save-draft rỗng cho user {}, bỏ qua.", user.getId());
            return;
        }

        try {
            record.setUserAnswersJson(objectMapper.writeValueAsString(request.getAnswers()));
            record.setUpdatedAt(LocalDateTime.now());
            recordRepository.save(record);
        } catch (JsonProcessingException e) {
            log.error("Lỗi lưu nháp câu trả lời Writing", e);
        }
    }

    @Transactional
    public WritingPracticeResultResponseDto submitDailyPractice(DailyReviewSubmissionRequestDto request) {
        User user = SecurityUtils.getCurrentUser();
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();

        // 1. TÌM VÀ KHÓA RECORD ĐỀ BÀI
        WritingTestRecord record = recordRepository
                .findFirstByUserIdAndTestTypeAndTestDateAndStatus(
                        user.getId(), TEST_TYPE_DAILY, today, TestRecordStatus.IN_PROGRESS)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy bài luyện tập đang làm dở. Vui lòng tải lại trang!"));

        if (request == null || request.getAnswers() == null || request.getAnswers().isEmpty()) {
            throw new ValidationException("Bạn chưa nộp câu trả lời nào. Vui lòng làm bài trước khi nộp!");
        }

        // 2. ĐỌC KHUNG ĐỀ ĐÃ FREEZE LÚC BẮT ĐẦU (chứa quyết định lộ/ém requiredGrammar)
        Map<Long, WritingQuestionDto> frozenMap = readFrozenQuestions(record);

        // 3. LOAD CÂU HỎI THẬT
        List<Long> questionIds = request.getAnswers().stream()
                .map(UserAnswerDto::getQuestionId)
                .toList();
        Map<Long, WritingQuestion> questionMap = questionRepository.findAllById(questionIds).stream()
                .collect(Collectors.toMap(WritingQuestion::getId, q -> q));

        // 4. GOM DỮ LIỆU ĐỂ GỬI AI
        List<Map<String, Object>> aiInputData = new ArrayList<>();
        List<String> imageUrlsToDownload = new ArrayList<>();

        for (UserAnswerDto answer : request.getAnswers()) {
            WritingQuestion q = questionMap.get(answer.getQuestionId());
            if (q == null) continue;

            WritingQuestionDto frozen = frozenMap.get(answer.getQuestionId());
            // Nếu khung đề hỏng thì coi như câu random (ém ngữ pháp) cho an toàn
            String requiredGrammar = frozen != null ? frozen.getRequiredGrammar() : null;

            Map<String, Object> inputMap = new HashMap<>();
            inputMap.put("questionId", q.getId());
            inputMap.put("userSentence", answer.getSelectedAnswer());
            inputMap.put("givenWords", q.getGivenWords());
            inputMap.put("requiredGrammar", requiredGrammar);

            aiInputData.add(inputMap);
            imageUrlsToDownload.add(q.getImageUrl());
        }

        // 5. GỌI AI CHẤM ĐIỂM (dùng chung prompt với WritingTestService nhưng đã tách riêng bản Practice)
        List<WritingAiGradingResponseDto> aiResults = gradeWithAi(aiInputData, imageUrlsToDownload);

        // 6. TỔNG HỢP ĐIỂM, XP, SM-2 VÀ LỊCH SỬ
        int totalScore = 0;
        int totalEarnedXp = 0;
        List<QuestionReviewDto> reviewList = new ArrayList<>();
        List<UserWritingQuestionHistory> historiesToSave = new ArrayList<>();
        Map<String, UserLearningProgress> progressCache = new HashMap<>();
        Set<String> processedSm2KeysThisSession = new HashSet<>();

        for (WritingAiGradingResponseDto aiResult : aiResults) {
            if (aiResult.getQuestionId() == null) continue;

            int score = clampScore(aiResult.getScore());
            totalScore += score;
            totalEarnedXp += calculateXp(score);

            // Chỉ đạt 3/3 mới được tính là "đúng" cho SM-2
            boolean isCorrect = (score == MAX_SCORE_PER_QUESTION);

            WritingQuestion question = questionMap.get(aiResult.getQuestionId());
            KnowledgeItem knowledgeItem = resolveKnowledgeItem(question, aiResult.getWeakness());

            if (knowledgeItem != null) {
                String cacheKey = ProgressCacheKeyUtil.buildKey(knowledgeItem.getId(), null);
                if (!processedSm2KeysThisSession.contains(cacheKey)) {
                    UserLearningProgress progress = DailyReviewProgressHelper.resolveOrCreateProgress(
                            user, knowledgeItem, null, progressCache,
                            progressRepository, ToeicPart.WRITING_PART_1);
                    spacedRepetitionService.updateProgress(progress, isCorrect);
                    processedSm2KeysThisSession.add(cacheKey);
                }
            } else {
                log.warn("Câu Writing {} không có knowledgeItem và AI cũng không chẩn đoán được điểm yếu, bỏ qua SM-2",
                        aiResult.getQuestionId());
            }

            if (question != null) {
                UserWritingQuestionHistory history = new UserWritingQuestionHistory();
                history.setUserId(user.getId());
                history.setQuestionId(question.getId());
                history.setCorrect(isCorrect);
                history.setAnsweredAt(now);
                historiesToSave.add(history);
            }

            // ---- DTO xem lại bài ----
            QuestionReviewDto reviewDto = new QuestionReviewDto();
            reviewDto.setQuestionId(aiResult.getQuestionId());
            reviewDto.setUserSelectedAnswer(request.getAnswers().stream()
                    .filter(a -> a.getQuestionId().equals(aiResult.getQuestionId()))
                    .map(UserAnswerDto::getSelectedAnswer)
                    .findFirst().orElse(""));
            reviewDto.setCorrectAnswer("Điểm: " + score + "/" + MAX_SCORE_PER_QUESTION);
            reviewDto.setExplanation(aiResult.getFeedback());
            reviewDto.setCorrect(isCorrect);
            if (aiResult.getWeakness() != null && aiResult.getWeakness().getKnowledgeName() != null) {
                reviewDto.setKnowledgeName(aiResult.getWeakness().getKnowledgeName());
            } else if (knowledgeItem != null) {
                reviewDto.setKnowledgeName(knowledgeItem.getKnowledgeName());
            }
            reviewList.add(reviewDto);
        }

        writingHistoryRepository.saveAll(historiesToSave);
        progressRepository.saveAll(progressCache.values());

        // 7. ÁP DỤNG LUẬT CHỐNG SPAM (đọc từ DB)
        int totalQuestions = reviewList.isEmpty() ? 0 : reviewList.size();
        int maxScore = totalQuestions * MAX_SCORE_PER_QUESTION;
        int scorePercent = maxScore > 0 ? (totalScore * 100) / maxScore : 0;
        int minScorePercent = getIntConfig("MIN_PRACTICE_SCORE_PERCENT", 10);
        boolean isValidEffort = scorePercent >= minScorePercent;

        if (isValidEffort) {
            user.setTotalXp(user.getTotalXp() + totalEarnedXp);
        } else {
            totalEarnedXp = 0;
            log.warn("User {} nộp bài luyện tập Writing không đạt chuẩn ({}%). Hủy toàn bộ XP kiếm được.",
                    user.getEmail(), scorePercent);
        }
        userRepository.save(user);

        // 8. CHỐT SỐ ĐỀ ĐÃ THI VÀO DATABASE
        record.setStatus(TestRecordStatus.COMPLETED);
        record.setScore(totalScore);
        record.setTotalQuestions(totalQuestions);
        record.setUpdatedAt(now);
        try {
            Map<Long, String> answerMap = request.getAnswers().stream()
                    .collect(Collectors.toMap(UserAnswerDto::getQuestionId,
                            UserAnswerDto::getSelectedAnswer, (a, b) -> a, LinkedHashMap::new));
            record.setUserAnswersJson(objectMapper.writeValueAsString(answerMap));
            record.setReviewJson(objectMapper.writeValueAsString(reviewList));
        } catch (JsonProcessingException e) {
            log.error("Lỗi lưu JSON chốt điểm Writing", e);
        }
        recordRepository.save(record);

        log.info("User {} hoàn thành luyện tập Writing: {}/{} điểm, +{} XP (hợp lệ={})",
                user.getId(), totalScore, maxScore, totalEarnedXp, isValidEffort);

        return new WritingPracticeResultResponseDto(
                totalQuestions, totalScore, maxScore, reviewList, isValidEffort, totalEarnedXp);
    }

    @Transactional(readOnly = true)
    public List<WritingPracticeHistoryDto> getPracticeHistory() {
        User user = SecurityUtils.getCurrentUser();

        return recordRepository.findByUserIdAndTestTypeOrderByTestDateDesc(user.getId(), TEST_TYPE_DAILY)
                .stream()
                .map(record -> {
                    WritingPracticeHistoryDto dto = new WritingPracticeHistoryDto();
                    dto.setRecordId(record.getId());
                    dto.setStatus(record.getStatus().name());
                    dto.setTestDate(record.getTestDate().toString());
                    dto.setScore(record.getScore());
                    dto.setTotalQuestions(record.getTotalQuestions());
                    dto.setReviewJson(record.getReviewJson());
                    dto.setQuestionsJson(record.getQuestionsJson());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    // =================================================================================================
    // HÀM PHỤ TRỢ
    // =================================================================================================

    /**
     * Gọi AI chấm điểm, có retry tối đa 5 lần. Trả về null/rỗng thì ném lỗi tiếng Việt.
     */
    private List<WritingAiGradingResponseDto> gradeWithAi(
            List<Map<String, Object>> aiInputData, List<String> imageUrls) {
        if (aiInputData.isEmpty()) {
            return List.of();
        }

        final String[] aiResponseJsonHolder = new String[1];

        try {
            String gradingPrompt = AIPromptTemplates.buildWritingPart1PracticeGradingPrompt();
            String finalPrompt = gradingPrompt + "\n\nĐây là inputData của thí sinh:\n"
                    + objectMapper.writeValueAsString(aiInputData);

            RetryExecutor.executeWithRetry(
                    () -> {
                        String result;
                        try {
                            // Chấm P1 có xoay model/key khi dính 429, ném lỗi để retry ngoài thử lại
                            result = aiService.gradeWritingPart1Resilient(
                                    finalPrompt, imageUrls);
                        } catch (Exception ex) {
                            throw new RuntimeException(ex.getMessage(), ex);
                        }
                        if (result == null || result.trim().isEmpty()) {
                            throw new RuntimeException("AI trả về kết quả rỗng hoặc null, cần thử lại.");
                        }
                        aiResponseJsonHolder[0] = result;
                    },
                    "AI Chấm điểm Writing Part 1 Practice",
                    MAX_GRADING_RETRIES,
                    GRADING_RETRY_DELAY_MS,
                    GRADING_RETRY_BACKOFF_MS
            );
        } catch (Exception e) {
            log.error("Lỗi đóng gói dữ liệu hoặc quá trình thử lại thất bại: ", e);
            throw new RuntimeException("Hệ thống chấm thi AI đang quá tải, vui lòng thử lại sau.");
        }

        String aiResponseJson = aiResponseJsonHolder[0];
        if (aiResponseJson == null || aiResponseJson.isEmpty()) {
            // Mọi retry + xoay model/key đều chết: trả 429 rõ ràng thay vì 500,
            // bài làm không mất (record vẫn IN_PROGRESS, user nộp lại được)
            throw new QuotaExceededException(
                    "Hệ thống AI chấm bài đang quá tải (hết quota), vui lòng đợi ít phút rồi nộp lại.");
        }

        try {
            return objectMapper.readValue(aiResponseJson, new TypeReference<List<WritingAiGradingResponseDto>>() {});
        } catch (JsonProcessingException e) {
            log.error("Lỗi parse JSON từ AI", e);
            throw new RuntimeException("AI trả về kết quả lỗi định dạng, vui lòng thử lại.");
        }
    }

    /**
     * Chốt nguồn SM-2 cho một câu:
     * Ưu tiên knowledgeItem của chính câu hỏi (đúng với mảng ngữ pháp mà hệ thống bốc câu).
     * Chỉ khi câu không gắn knowledgeItem mới fallback sang điểm yếu AI chẩn đoán.
     */
    private KnowledgeItem resolveKnowledgeItem(
            WritingQuestion question, WritingAiGradingResponseDto.Weakness weakness) {
        if (question != null && question.getKnowledgeItem() != null) {
            return question.getKnowledgeItem();
        }
        if (weakness == null || weakness.getKnowledgeType() == null || weakness.getKnowledgeType().isBlank()) {
            return null;
        }
        return getOrCreateKnowledgeItem(weakness);
    }

    private KnowledgeItem getOrCreateKnowledgeItem(WritingAiGradingResponseDto.Weakness weakness) {
        try {
            KnowledgeType type = KnowledgeType.valueOf(weakness.getKnowledgeType());
            String knowledgeName = weakness.getKnowledgeName() != null && !weakness.getKnowledgeName().isBlank()
                    ? weakness.getKnowledgeName()
                    : null;

            return knowledgeItemRepository.findAll().stream()
                    .filter(k -> k.getKnowledgeType() == type && k.getPurpose() == Purpose.PRACTICE)
                    .findFirst().orElseGet(() -> {
                        KnowledgeItem newKi = new KnowledgeItem();
                        newKi.setKnowledgeType(type);
                        newKi.setKnowledgeName(knowledgeName);
                        newKi.setPurpose(Purpose.PRACTICE);
                        return knowledgeItemRepository.save(newKi);
                    });
        } catch (IllegalArgumentException e) {
            log.warn("AI trả về knowledgeType không hợp lệ: {}. Bỏ qua SM-2 cho câu này.", weakness.getKnowledgeType());
            return null;
        }
    }

    /** AI có thể trả về điểm ngoài thang 0-3, cần chặn lại cho an toàn. */
    private int clampScore(Integer rawScore) {
        if (rawScore == null) {
            return 0;
        }
        return Math.max(0, Math.min(MAX_SCORE_PER_QUESTION, rawScore));
    }

    /** XP theo thang 0-3: 3 điểm +10, 2 điểm +6, 1 điểm +2, 0 điểm +0. */
    private int calculateXp(int score) {
        return switch (score) {
            case 3 -> 10;
            case 2 -> 6;
            case 1 -> 2;
            default -> 0;
        };
    }

    private int getIntConfig(String key, int defaultValue) {
        return appConfigRepository.findById(key)
                .map(config -> {
                    try {
                        return Integer.parseInt(config.getConfigValue().trim());
                    } catch (NumberFormatException e) {
                        log.warn("Giá trị app_configs.{} không phải số: {}. Dùng mặc định {}.",
                                key, config.getConfigValue(), defaultValue);
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }

    private Map<Long, WritingQuestionDto> readFrozenQuestions(WritingTestRecord record) {
        if (record.getQuestionsJson() == null || record.getQuestionsJson().isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(record.getQuestionsJson(),
                    new TypeReference<List<WritingQuestionDto>>() {})
                    .stream()
                    .filter(dto -> dto.getQuestionId() != null)
                    .collect(Collectors.toMap(WritingQuestionDto::getQuestionId, dto -> dto, (a, b) -> a));
        } catch (JsonProcessingException e) {
            log.error("Lỗi parse khung đề Writing", e);
            return new HashMap<>();
        }
    }

    private WritingQuestionDto convertToDto(WritingQuestion q, String requiredGrammar) {
        WritingQuestionDto dto = new WritingQuestionDto();
        dto.setQuestionId(q.getId());
        dto.setImageUrl(q.getImageUrl());
        dto.setGivenWords(q.getGivenWords());
        dto.setRequiredGrammar(requiredGrammar);
        if (q.getKnowledgeItem() != null) {
            dto.setKnowledgeItemId(q.getKnowledgeItem().getId());
        }
        return dto;
    }

    private WritingPracticeSessionDto buildSessionDto(WritingTestRecord record) {
        WritingPracticeSessionDto response = new WritingPracticeSessionDto();
        response.setRecordId(record.getId());
        response.setStatus(record.getStatus().name());

        try {
            List<WritingQuestionDto> questions = objectMapper.readValue(
                    record.getQuestionsJson(), new TypeReference<List<WritingQuestionDto>>() {});
            response.setQuestions(questions);
        } catch (JsonProcessingException e) {
            log.error("Lỗi parse JSON đề luyện tập Writing", e);
            throw new RuntimeException("Lỗi hệ thống khi tải đề luyện tập. Vui lòng thử lại sau!");
        }

        if (record.getUserAnswersJson() != null && !record.getUserAnswersJson().isEmpty()) {
            try {
                response.setSavedAnswers(objectMapper.readValue(
                        record.getUserAnswersJson(), new TypeReference<Map<Long, String>>() {}));
            } catch (JsonProcessingException e) {
                log.error("Lỗi parse JSON nháp câu trả lời Writing", e);
                response.setSavedAnswers(new HashMap<>());
            }
        }

        return response;
    }
}
