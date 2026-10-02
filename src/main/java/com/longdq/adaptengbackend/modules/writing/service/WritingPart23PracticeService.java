package com.longdq.adaptengbackend.modules.writing.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.longdq.adaptengbackend.common.ai.AIPromptTemplates;
import com.longdq.adaptengbackend.common.ai.AIService;
import com.longdq.adaptengbackend.common.dto.DailyReviewSubmissionRequestDto;
import com.longdq.adaptengbackend.common.dto.QuestionReviewDto;
import com.longdq.adaptengbackend.common.dto.SaveDraftRequestDto;
import com.longdq.adaptengbackend.common.dto.UserAnswerDto;
import com.longdq.adaptengbackend.common.enums.KnowledgeType;
import com.longdq.adaptengbackend.common.enums.Level;
import com.longdq.adaptengbackend.common.enums.TestRecordStatus;
import com.longdq.adaptengbackend.common.enums.ToeicPart;
import com.longdq.adaptengbackend.common.exception.ForbiddenException;
import com.longdq.adaptengbackend.common.exception.QuotaExceededException;
import com.longdq.adaptengbackend.common.exception.ResourceNotFoundException;
import com.longdq.adaptengbackend.common.exception.ValidationException;
import com.longdq.adaptengbackend.common.security.PremiumCheckUtil;
import com.longdq.adaptengbackend.common.security.SecurityUtils;
import com.longdq.adaptengbackend.common.util.DailyReviewProgressHelper;
import com.longdq.adaptengbackend.common.util.ProgressCacheKeyUtil;
import com.longdq.adaptengbackend.common.util.RetryExecutor;
import com.longdq.adaptengbackend.modules.progress.repository.AppConfigRepository;
import com.longdq.adaptengbackend.modules.spacedrepetition.entity.KnowledgeItem;
import com.longdq.adaptengbackend.modules.spacedrepetition.entity.UserLearningProgress;
import com.longdq.adaptengbackend.modules.spacedrepetition.repository.UserLearningProgressRepository;
import com.longdq.adaptengbackend.modules.spacedrepetition.service.SpacedRepetitionService;
import com.longdq.adaptengbackend.modules.user.entity.User;
import com.longdq.adaptengbackend.modules.user.repository.UserRepository;
import com.longdq.adaptengbackend.modules.writing.dto.WritingAiGradingResponseDto;
import com.longdq.adaptengbackend.modules.writing.dto.WritingPart23QuestionDto;
import com.longdq.adaptengbackend.modules.writing.dto.WritingPracticeHistoryDto;
import com.longdq.adaptengbackend.modules.writing.dto.WritingPart23ResultDto;
import com.longdq.adaptengbackend.modules.writing.dto.WritingPart23SessionDto;
import com.longdq.adaptengbackend.modules.writing.entity.UserWritingQuestionHistory;
import com.longdq.adaptengbackend.modules.writing.entity.WritingQuestion;
import com.longdq.adaptengbackend.modules.writing.entity.WritingTestRecord;
import com.longdq.adaptengbackend.modules.writing.repository.UserWritingQuestionHistoryRepository;
import com.longdq.adaptengbackend.modules.writing.repository.WritingQuestionRepository;
import com.longdq.adaptengbackend.modules.writing.repository.WritingTestRecordRepository;
import com.longdq.adaptengbackend.modules.writing.service.WritingPart23TestService.PureGrade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * LUYỆN TẬP HÀNG NGÀY HỖN HỢP: session 5 câu, P2/P3 gắn required_constraints từ SM-2 due.
 * P1 chấm tự do bằng Vision (requiredGrammar = null, prompt Practice).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WritingPart23PracticeService {

    public static final String TEST_TYPE_DAILY_COMBINED = "WRITING_P23_DAILY";

    private final WritingQuestionRepository questionRepository;
    private final WritingTestRecordRepository recordRepository;
    private final UserWritingQuestionHistoryRepository historyRepository;
    private final UserLearningProgressRepository progressRepository;
    private final UserRepository userRepository;
    private final SpacedRepetitionService spacedRepetitionService;
    private final WritingPart23SessionAssembler assembler;
    private final WritingPart23TestService testService;
    private final WritingPart23GradingHelper gradingHelper;
    private final PremiumCheckUtil premiumCheckUtil;
    private final AppConfigRepository appConfigRepository;
    private final AIService aiService;
    private final ObjectMapper objectMapper;

    private static final long FREE_DAILY_LIMIT = 1;
    private static final long VIP_DAILY_LIMIT = 3;
    private static final int MAX_GRADING_RETRIES = 3;
    private static final long GRADING_RETRY_DELAY_MS = 10000;
    private static final long GRADING_RETRY_BACKOFF_MS = 2000;
    // Số combo model/key tối đa cho mỗi lần gọi chấm (retry ngoài bao thêm, tổng vẫn trong timeout 120s của FE)
    private static final int GRADING_FALLBACK_ATTEMPTS = 8;

    @Transactional
    public WritingPart23SessionDto getOrCreateDailyPractice() {
        User user = SecurityUtils.getCurrentUser();
        LocalDate today = LocalDate.now();

        List<WritingTestRecord> olds = recordRepository
                .findByUserIdAndTestTypeAndStatusAndTestDateLessThan(
                        user.getId(), TEST_TYPE_DAILY_COMBINED, TestRecordStatus.IN_PROGRESS, today);
        if (!olds.isEmpty()) {
            for (WritingTestRecord o : olds) {
                o.setStatus(TestRecordStatus.EXPIRED);
                o.setUpdatedAt(LocalDateTime.now());
            }
            recordRepository.saveAll(olds);
        }

        Optional<WritingTestRecord> pending = recordRepository
                .findFirstByUserIdAndTestTypeAndTestDateAndStatus(
                        user.getId(), TEST_TYPE_DAILY_COMBINED, today, TestRecordStatus.IN_PROGRESS);
        if (pending.isPresent()) {
            return buildSessionDto(pending.get());
        }

        checkDailyQuota(user.getId(), today);
        Level level = user.getWritingCurrentLevel();
        if (level == null) {
            throw new ForbiddenException("REQUIRE_WRITING_PLACEMENT_TEST");
        }

        List<Long> picked = new ArrayList<>(List.of(-1L));
        List<WritingQuestion> p1 = assembler.pickPart1Questions(level, user.getId(), picked);
        p1.forEach(q -> picked.add(q.getId()));
        WritingQuestion p2 = assembler.pickOneQuestion(ToeicPart.WRITING_PART_2, level, user.getId(), picked);
        if (p2 != null) {
            picked.add(p2.getId());
        }
        WritingQuestion p3 = assembler.pickOneQuestion(ToeicPart.WRITING_PART_3, level, user.getId(), picked);
        if (p1.size() < 3 || p2 == null || p3 == null) {
            throw new ValidationException(
                    "Hệ thống chưa đủ câu hỏi Writing (3×P1 + P2 + P3) cho trình độ của bạn. Vui lòng thử lại sau!");
        }

        // Gắn constraints từ SM-2 due (P2 tối đa 2, P3 tối đa 5). Đề vẫn generic, chỉ ép lúc làm bài.
        List<String> p2Constraints = assembler.loadDueConstraints(user.getId(), ToeicPart.WRITING_PART_2, 2);
        List<String> p3Constraints = assembler.loadDueConstraints(user.getId(), ToeicPart.WRITING_PART_3, 5);

        List<WritingPart23QuestionDto> dtos = new ArrayList<>();
        for (WritingQuestion q : p1) {
            dtos.add(assembler.toDto(q, List.of()));
        }
        dtos.add(assembler.toDto(p2, p2Constraints));
        dtos.add(assembler.toDto(p3, p3Constraints));

        WritingTestRecord record = new WritingTestRecord();
        record.setUserId(user.getId());
        record.setTestType(TEST_TYPE_DAILY_COMBINED);
        record.setTestDate(today);
        record.setStatus(TestRecordStatus.IN_PROGRESS);
        record.setLevel(level);
        record.setTotalQuestions(dtos.size());
        try {
            record.setQuestionsJson(objectMapper.writeValueAsString(dtos));
        } catch (Exception e) {
            log.error("Lỗi serialize đề Daily hỗn hợp", e);
            throw new RuntimeException("Lỗi hệ thống khi tạo đề luyện tập. Vui lòng thử lại sau!");
        }
        record = recordRepository.save(record);
        log.info("Đã tạo đề Daily hỗn hợp cho user {} (P2 constraints={}, P3 constraints={})",
                user.getId(), p2Constraints, p3Constraints);
        return buildSessionDto(record);
    }

    @Transactional
    public void saveDraft(SaveDraftRequestDto request) {
        User user = SecurityUtils.getCurrentUser();
        WritingTestRecord record = recordRepository
                .findFirstByUserIdAndTestTypeAndTestDateAndStatus(
                        user.getId(), TEST_TYPE_DAILY_COMBINED, LocalDate.now(), TestRecordStatus.IN_PROGRESS)
                .orElse(null);
        if (record == null || request == null || request.getAnswers() == null) {
            return;
        }
        try {
            record.setUserAnswersJson(objectMapper.writeValueAsString(request.getAnswers()));
            record.setUpdatedAt(LocalDateTime.now());
            recordRepository.save(record);
        } catch (Exception e) {
            log.error("Lỗi lưu nháp Daily hỗn hợp", e);
        }
    }

    @Transactional
    public WritingPart23ResultDto submitDailyPractice(DailyReviewSubmissionRequestDto request) {
        User user = SecurityUtils.getCurrentUser();
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();

        WritingTestRecord record = recordRepository
                .findFirstByUserIdAndTestTypeAndTestDateAndStatus(
                        user.getId(), TEST_TYPE_DAILY_COMBINED, today, TestRecordStatus.IN_PROGRESS)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy bài luyện tập đang làm dở. Vui lòng tải lại trang!"));
        if (request == null || request.getAnswers() == null || request.getAnswers().isEmpty()) {
            throw new ValidationException("Bạn chưa nộp câu trả lời nào. Vui lòng làm bài trước khi nộp!");
        }

        List<WritingPart23QuestionDto> frozen = readFrozen(record);
        Map<Long, String> answerMap = request.getAnswers().stream()
                .collect(Collectors.toMap(UserAnswerDto::getQuestionId, UserAnswerDto::getSelectedAnswer, (a, b) -> a));

        List<QuestionReviewDto> reviewList = new ArrayList<>();
        Map<String, UserLearningProgress> progressCache = new HashMap<>();
        Set<String> processedKeys = new HashSet<>();
        List<UserWritingQuestionHistory> histories = new ArrayList<>();

        int p1Score = gradePart1Daily(frozen, answerMap, user, reviewList, progressCache, processedKeys, histories, now);
        int p2Score = 0;
        int p3Score = 0;
        int earnedXp = p1Xp(p1Score);

        for (WritingPart23QuestionDto d : frozen) {
            boolean isP2 = ToeicPart.WRITING_PART_2.name().equals(d.getToeicPart());
            boolean isP3 = ToeicPart.WRITING_PART_3.name().equals(d.getToeicPart());
            if (!isP2 && !isP3) {
                continue;
            }
            List<String> required = d.getRequiredConstraints() != null ? d.getRequiredConstraints() : List.of();
            String answer = answerMap.getOrDefault(d.getQuestionId(), "");
            ToeicPart part = isP2 ? ToeicPart.WRITING_PART_2 : ToeicPart.WRITING_PART_3;
            int max = isP2 ? 4 : 5;
            PureGrade g = isP2 ? testService.gradePart2Pure(d, answer, required)
                    : testService.gradePart3Pure(d, answer, required);

            if (isP2) {
                p2Score += g.finalScore;
            } else {
                p3Score += g.finalScore;
            }
            earnedXp += g.finalScore * 3;

            // Cập nhật SM-2 cho constraints (pass/fail từ AI), rồi lưu lỗi mới
            for (String req : required) {
                boolean passed = Boolean.TRUE.equals(g.passes.get(req));
                try {
                    KnowledgeType type = KnowledgeType.valueOf(req);
                    KnowledgeItem ki = gradingHelper.getOrCreateKnowledgeItem(type);
                    String key = ProgressCacheKeyUtil.buildKey(ki.getId(), null);
                    if (processedKeys.contains(key)) {
                        continue;
                    }
                    UserLearningProgress progress = DailyReviewProgressHelper.resolveOrCreateProgress(
                            user, ki, null, progressCache, progressRepository, part);
                    spacedRepetitionService.updateProgress(progress, passed);
                    processedKeys.add(key);
                } catch (IllegalArgumentException e) {
                    log.warn("Constraint không hợp lệ: {}. Bỏ qua SM-2.", req);
                }
            }
            for (KnowledgeType t : g.weaknesses) {
                KnowledgeItem ki = gradingHelper.getOrCreateKnowledgeItem(t);
                String key = ProgressCacheKeyUtil.buildKey(ki.getId(), null);
                if (processedKeys.contains(key)) {
                    continue;
                }
                UserLearningProgress progress = DailyReviewProgressHelper.resolveOrCreateProgress(
                        user, ki, null, progressCache, progressRepository, part);
                // Lỗi mới: khởi tạo vòng lặp (fail 1 lần để interval = 1)
                spacedRepetitionService.updateProgress(progress, false);
                // Giữ ease gốc 2.5 cho lần đầu thay vì bị trừ như fail thông thường
                if (progress.getRepetitionCount() != null && progress.getRepetitionCount() == 0
                        && progress.getIntervalDays() != null && progress.getIntervalDays() == 1) {
                    progress.setEaseFactor(2.5);
                }
                processedKeys.add(key);
            }

            QuestionReviewDto review = new QuestionReviewDto();
            review.setQuestionId(d.getQuestionId());
            review.setUserSelectedAnswer(answer);
            review.setCorrectAnswer("Điểm: " + g.finalScore + "/" + max);
            review.setExplanation(g.feedbackWithNote
                    + (g.wordCount != null ? " (Số từ AI đếm: " + g.wordCount + ")" : ""));
            review.setCorrect(g.finalScore == max);
            if (!g.weaknesses.isEmpty()) {
                review.setKnowledgeName(g.weaknesses.get(0).name());
            }
            reviewList.add(review);
            histories.add(newHistory(user.getId(), d.getQuestionId(), g.finalScore == max, now));
        }

        historyRepository.saveAll(histories);
        progressRepository.saveAll(progressCache.values());

        int totalScore = p1Score + p2Score + p3Score;
        int maxScore = 9 + 4 + 5;
        int scorePercent = maxScore > 0 ? (totalScore * 100) / maxScore : 0;
        boolean isValidEffort = scorePercent >= getIntConfig("MIN_PRACTICE_SCORE_PERCENT", 10);
        if (isValidEffort) {
            user.setTotalXp(user.getTotalXp() + earnedXp);
        } else {
            earnedXp = 0;
            log.warn("User {} nộp Daily hỗn hợp không đạt chuẩn ({}%). Hủy XP.", user.getEmail(), scorePercent);
        }
        userRepository.save(user);

        record.setStatus(TestRecordStatus.COMPLETED);
        record.setScore(totalScore);
        record.setTotalQuestions(frozen.size());
        record.setUpdatedAt(now);
        try {
            record.setReviewJson(objectMapper.writeValueAsString(reviewList));
        } catch (Exception e) {
            log.error("Lỗi lưu JSON chốt điểm Daily hỗn hợp", e);
        }
        recordRepository.save(record);

        return new WritingPart23ResultDto(frozen.size(), totalScore, maxScore,
                p1Score, p2Score, p3Score, reviewList, isValidEffort, earnedXp, null, null);
    }

    /** Chấm P1 Daily bằng Vision + prompt Practice (tự do, ém requiredGrammar). */
    private int gradePart1Daily(List<WritingPart23QuestionDto> frozen, Map<Long, String> answerMap, User user,
            List<QuestionReviewDto> reviewList, Map<String, UserLearningProgress> progressCache,
            Set<String> processedKeys, List<UserWritingQuestionHistory> histories, LocalDateTime now) {
        List<WritingPart23QuestionDto> p1Dtos = frozen.stream()
                .filter(d -> ToeicPart.WRITING_PART_1.name().equals(d.getToeicPart())).toList();
        if (p1Dtos.isEmpty()) {
            return 0;
        }
        List<Long> ids = p1Dtos.stream().map(WritingPart23QuestionDto::getQuestionId).toList();
        Map<Long, WritingQuestion> qMap = questionRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(WritingQuestion::getId, q -> q, (a, b) -> a));

        List<Map<String, Object>> aiInput = new ArrayList<>();
        List<String> imageUrls = new ArrayList<>();
        for (WritingPart23QuestionDto d : p1Dtos) {
            WritingQuestion q = qMap.get(d.getQuestionId());
            if (q == null) {
                continue;
            }
            Map<String, Object> in = new HashMap<>();
            in.put("questionId", q.getId());
            in.put("userSentence", answerMap.getOrDefault(d.getQuestionId(), ""));
            in.put("givenWords", q.getGivenWords());
            in.put("requiredGrammar", null);
            aiInput.add(in);
            imageUrls.add(q.getImageUrl());
        }
        List<WritingAiGradingResponseDto> results = gradePart1PracticeWithAi(aiInput, imageUrls);

        int sum = 0;
        for (WritingAiGradingResponseDto r : results) {
            int score = Math.max(0, Math.min(3, r.getScore() == null ? 0 : r.getScore()));
            sum += score;
            boolean isCorrect = (score == 3);
            KnowledgeItem ki = resolveKnowledgeItem(qMap.get(r.getQuestionId()), r.getWeakness());
            if (ki != null) {
                String key = ProgressCacheKeyUtil.buildKey(ki.getId(), null);
                if (!processedKeys.contains(key)) {
                    UserLearningProgress progress = DailyReviewProgressHelper.resolveOrCreateProgress(
                            user, ki, null, progressCache, progressRepository, ToeicPart.WRITING_PART_1);
                    spacedRepetitionService.updateProgress(progress, isCorrect);
                    processedKeys.add(key);
                }
            }
            histories.add(newHistory(user.getId(), r.getQuestionId(), isCorrect, now));
            QuestionReviewDto review = new QuestionReviewDto();
            review.setQuestionId(r.getQuestionId());
            review.setUserSelectedAnswer(answerMap.getOrDefault(r.getQuestionId(), ""));
            review.setCorrectAnswer("Điểm: " + score + "/3");
            review.setExplanation(r.getFeedback());
            review.setCorrect(isCorrect);
            if (r.getWeakness() != null) {
                review.setKnowledgeName(r.getWeakness().getKnowledgeName());
            }
            reviewList.add(review);
        }
        return sum;
    }

    private List<WritingAiGradingResponseDto> gradePart1PracticeWithAi(
            List<Map<String, Object>> aiInput, List<String> imageUrls) {
        if (aiInput.isEmpty()) {
            return List.of();
        }
        final String[] holder = new String[1];
        try {
            String finalPrompt = AIPromptTemplates.buildWritingPart1PracticeGradingPrompt()
                    + "\n\nĐây là inputData của thí sinh:\n" + objectMapper.writeValueAsString(aiInput);
            RetryExecutor.executeWithRetry(() -> {
                String result;
                try {
                    // Chấm P1 có xoay model/key khi dính 429, ném lỗi để retry ngoài thử lại
                    result = aiService.gradeWritingPart1Resilient(
                            finalPrompt, imageUrls);
                } catch (Exception e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
                if (result == null || result.trim().isEmpty()) {
                    throw new RuntimeException("AI trả về rỗng, cần thử lại.");
                }
                holder[0] = result;
            }, "AI Chấm điểm Writing P1 Daily hỗn hợp", MAX_GRADING_RETRIES,
                    GRADING_RETRY_DELAY_MS, GRADING_RETRY_BACKOFF_MS);
        } catch (Exception e) {
            log.error("Lỗi chấm P1 Daily hỗn hợp: ", e);
            throw new RuntimeException("Hệ thống chấm thi AI đang quá tải, vui lòng thử lại sau.");
        }
        // Guard null: RetryExecutor nuốt lỗi sau khi bỏ cuộc — trả 429 rõ ràng thay vì
        // để readValue(null) crash 500 IllegalArgumentException
        if (holder[0] == null || holder[0].isBlank()) {
            throw new QuotaExceededException(
                    "Hệ thống AI chấm bài đang quá tải (hết quota), vui lòng đợi ít phút rồi nộp lại.");
        }
        try {
            return objectMapper.readValue(holder[0],
                    new TypeReference<List<WritingAiGradingResponseDto>>() {});
        } catch (Exception e) {
            log.error("Lỗi parse JSON P1 Daily từ AI", e);
            throw new RuntimeException("AI trả về kết quả lỗi định dạng, vui lòng thử lại.");
        }
    }

    private KnowledgeItem resolveKnowledgeItem(WritingQuestion question,
            WritingAiGradingResponseDto.Weakness weakness) {
        if (question != null && question.getKnowledgeItem() != null) {
            return question.getKnowledgeItem();
        }
        if (weakness == null || weakness.getKnowledgeType() == null || weakness.getKnowledgeType().isBlank()) {
            return null;
        }
        try {
            KnowledgeType type = KnowledgeType.valueOf(weakness.getKnowledgeType());
            return gradingHelper.getOrCreateKnowledgeItem(type);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private UserWritingQuestionHistory newHistory(UUID userId, Long questionId, boolean correct, LocalDateTime now) {
        UserWritingQuestionHistory h = new UserWritingQuestionHistory();
        h.setUserId(userId);
        h.setQuestionId(questionId);
        h.setCorrect(correct);
        h.setAnsweredAt(now);
        return h;
    }

    private int p1Xp(int p1Score) {
        return switch (p1Score) {
            case 9 -> 30;
            case 8 -> 26;
            case 7 -> 22;
            case 6 -> 18;
            case 5 -> 14;
            case 4 -> 10;
            case 3 -> 7;
            case 2 -> 4;
            case 1 -> 2;
            default -> 0;
        };
    }

    private void checkDailyQuota(UUID userId, LocalDate today) {
        long completed = recordRepository.countByUserIdAndTestTypeAndTestDateAndStatus(
                userId, TEST_TYPE_DAILY_COMBINED, today, TestRecordStatus.COMPLETED);
        if (completed >= FREE_DAILY_LIMIT) {
            if (!premiumCheckUtil.isPremiumUser(userId)) {
                throw new ForbiddenException("REQUIRE_VIP");
            }
            if (completed >= VIP_DAILY_LIMIT) {
                throw new QuotaExceededException("MAX_LIMIT_REACHED");
            }
        }
    }

    private int getIntConfig(String key, int defaultValue) {
        return appConfigRepository.findById(key).map(c -> {
            try {
                return Integer.parseInt(c.getConfigValue().trim());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }).orElse(defaultValue);
    }

    @Transactional(readOnly = true)
    public List<WritingPracticeHistoryDto> getPracticeHistory() {
        User user = SecurityUtils.getCurrentUser();
        return recordRepository.findByUserIdAndTestTypeOrderByTestDateDesc(user.getId(), TEST_TYPE_DAILY_COMBINED)
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

    private List<WritingPart23QuestionDto> readFrozen(WritingTestRecord record) {
        try {
            return objectMapper.readValue(record.getQuestionsJson(),
                    new TypeReference<List<WritingPart23QuestionDto>>() {});
        } catch (Exception e) {
            log.error("Lỗi parse khung đề Daily hỗn hợp", e);
            return List.of();
        }
    }

    private WritingPart23SessionDto buildSessionDto(WritingTestRecord record) {
        WritingPart23SessionDto dto = new WritingPart23SessionDto();
        dto.setRecordId(record.getId());
        dto.setStatus(record.getStatus().name());
        dto.setTestType(record.getTestType());
        try {
            dto.setQuestions(objectMapper.readValue(record.getQuestionsJson(),
                    new TypeReference<List<WritingPart23QuestionDto>>() {}));
        } catch (Exception e) {
            log.error("Lỗi parse JSON session Daily hỗn hợp", e);
            throw new RuntimeException("Lỗi hệ thống khi tải đề luyện tập. Vui lòng thử lại sau!");
        }
        return dto;
    }
}
