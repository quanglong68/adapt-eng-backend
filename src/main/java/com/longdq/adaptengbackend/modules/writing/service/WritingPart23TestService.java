package com.longdq.adaptengbackend.modules.writing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.longdq.adaptengbackend.common.ai.AIPromptTemplates;
import com.longdq.adaptengbackend.common.ai.AIService;
import com.longdq.adaptengbackend.common.dto.QuestionReviewDto;
import com.longdq.adaptengbackend.common.dto.TestSubmissionRequestDto;
import com.longdq.adaptengbackend.common.dto.UserAnswerDto;
import com.longdq.adaptengbackend.common.enums.KnowledgeType;
import com.longdq.adaptengbackend.common.enums.Level;
import com.longdq.adaptengbackend.common.enums.Purpose;
import com.longdq.adaptengbackend.common.enums.TestRecordStatus;
import com.longdq.adaptengbackend.common.enums.ToeicPart;
import com.longdq.adaptengbackend.common.exception.QuotaExceededException;
import com.longdq.adaptengbackend.common.util.RetryExecutor;
import com.longdq.adaptengbackend.common.util.TestLevelEvaluationUtil;
import com.longdq.adaptengbackend.modules.spacedrepetition.entity.KnowledgeItem;
import com.longdq.adaptengbackend.modules.spacedrepetition.entity.UserLearningProgress;
import com.longdq.adaptengbackend.modules.spacedrepetition.repository.KnowledgeItemRepository;
import com.longdq.adaptengbackend.modules.spacedrepetition.repository.UserLearningProgressRepository;
import com.longdq.adaptengbackend.modules.user.entity.User;
import com.longdq.adaptengbackend.modules.user.repository.UserRepository;
import com.longdq.adaptengbackend.modules.writing.dto.WritingAiGradingResponseDto;
import com.longdq.adaptengbackend.modules.writing.dto.WritingPart23AiResultDto;
import com.longdq.adaptengbackend.modules.writing.dto.WritingPart23QuestionDto;
import com.longdq.adaptengbackend.modules.writing.dto.WritingPart23ResultDto;
import com.longdq.adaptengbackend.modules.writing.dto.WritingPart23SessionDto;
import com.longdq.adaptengbackend.modules.writing.entity.WritingQuestion;
import com.longdq.adaptengbackend.modules.writing.entity.WritingTestRecord;
import com.longdq.adaptengbackend.modules.writing.repository.WritingQuestionRepository;
import com.longdq.adaptengbackend.modules.writing.repository.WritingTestRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * LUỒNG TEST HỖN HỢP: ráp 3×P1 + 1×P2 + 1×P3, chấm không ràng buộc.
 * Tái dùng prompt/grading P1 cũ, P2/P3 chấm text-only với REQUIRED_CONSTRAINTS = [].
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WritingPart23TestService {

    public static final String TEST_TYPE_COMBINED = "WRITING_P23_MONTHLY";

    private final WritingQuestionRepository questionRepository;
    private final WritingTestRecordRepository recordRepository;
    private final KnowledgeItemRepository knowledgeItemRepository;
    private final UserLearningProgressRepository progressRepository;
    private final WritingPart23SessionAssembler assembler;
    private final WritingPart23GradingHelper gradingHelper;
    private final UserRepository userRepository;
    private final AIService aiService;
    private final ObjectMapper objectMapper;

    private static final int MAX_GRADING_RETRIES = 3;
    private static final long GRADING_RETRY_DELAY_MS = 10000;
    private static final long GRADING_RETRY_BACKOFF_MS = 2000;

    @Transactional
    public WritingPart23SessionDto generateCombinedTest(UUID userId, Level requestedLevel) {
        Optional<WritingTestRecord> existing = recordRepository.findByUserIdAndTestTypeAndStatus(
                userId, TEST_TYPE_COMBINED, TestRecordStatus.IN_PROGRESS);
        if (existing.isPresent()) {
            if (existing.get().getLevel() == requestedLevel) {
                log.info("Trả về bài Test hỗn hợp đang dở cho user {}", userId);
                return buildSessionDto(existing.get());
            }
            existing.get().setStatus(TestRecordStatus.EXPIRED);
            recordRepository.save(existing.get());
        }

        List<Long> picked = new ArrayList<>(List.of(-1L));
        List<WritingQuestion> p1 = assembler.pickPart1Questions(requestedLevel, userId, picked);
        p1.forEach(q -> picked.add(q.getId()));
        WritingQuestion p2 = assembler.pickOneQuestion(ToeicPart.WRITING_PART_2, requestedLevel, userId, picked);
        if (p2 != null) {
            picked.add(p2.getId());
        }
        WritingQuestion p3 = assembler.pickOneQuestion(ToeicPart.WRITING_PART_3, requestedLevel, userId, picked);

        if (p1.size() < 3 || p2 == null || p3 == null) {
            throw new RuntimeException("Hệ thống chưa đủ câu hỏi Writing (3×P1 + P2 + P3) cho level "
                    + requestedLevel.name() + ". Job 2AM sẽ bổ sung, vui lòng thử lại sau!");
        }

        List<WritingPart23QuestionDto> dtos = new ArrayList<>();
        for (WritingQuestion q : p1) {
            dtos.add(assembler.toDto(q, List.of()));
        }
        dtos.add(assembler.toDto(p2, List.of()));
        dtos.add(assembler.toDto(p3, List.of()));

        WritingTestRecord record = new WritingTestRecord();
        record.setUserId(userId);
        record.setTestType(TEST_TYPE_COMBINED);
        record.setLevel(requestedLevel);
        record.setStatus(TestRecordStatus.IN_PROGRESS);
        record.setTestDate(LocalDate.now());
        record.setTotalQuestions(dtos.size());
        try {
            record.setQuestionsJson(objectMapper.writeValueAsString(dtos));
        } catch (JsonProcessingException e) {
            log.error("Lỗi serialize đề Test hỗn hợp", e);
            throw new RuntimeException("Lỗi hệ thống khi tạo đề thi.");
        }
        record = recordRepository.save(record);
        return buildSessionDto(record);
    }

    @Transactional
    public WritingPart23ResultDto submitCombinedTest(TestSubmissionRequestDto request, User user) {
        WritingTestRecord record = recordRepository.findByUserIdAndTestTypeAndStatus(
                        user.getId(), TEST_TYPE_COMBINED, TestRecordStatus.IN_PROGRESS)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bài test hỗn hợp đang diễn ra!"));

        List<WritingPart23QuestionDto> frozen = readFrozen(record);
        Map<Long, String> answerMap = request.getAnswers().stream()
                .collect(Collectors.toMap(UserAnswerDto::getQuestionId, UserAnswerDto::getSelectedAnswer, (a, b) -> a));

        List<QuestionReviewDto> reviewList = new ArrayList<>();
        int p1Score = 0;
        int p2Score = 0;
        int p3Score = 0;

        List<WritingPart23QuestionDto> p1Dtos = frozen.stream()
                .filter(d -> ToeicPart.WRITING_PART_1.name().equals(d.getToeicPart())).toList();
        if (!p1Dtos.isEmpty()) {
            p1Score = gradePart1(p1Dtos, answerMap, user, reviewList);
        }

        Optional<WritingPart23QuestionDto> p2Dto = frozen.stream()
                .filter(d -> ToeicPart.WRITING_PART_2.name().equals(d.getToeicPart())).findFirst();
        if (p2Dto.isPresent()) {
            p2Score = gradePart2(p2Dto.get(), answerMap.getOrDefault(p2Dto.get().getQuestionId(), ""),
                    List.of(), user, reviewList);
        }

        Optional<WritingPart23QuestionDto> p3Dto = frozen.stream()
                .filter(d -> ToeicPart.WRITING_PART_3.name().equals(d.getToeicPart())).findFirst();
        if (p3Dto.isPresent()) {
            p3Score = gradePart3(p3Dto.get(), answerMap.getOrDefault(p3Dto.get().getQuestionId(), ""),
                    List.of(), user, reviewList);
        }

        int totalScore = p1Score + p2Score + p3Score;
        int maxScore = 9 + 4 + 5;

        // Trang thi hỗn hợp thay thế placement cũ: đạt ngưỡng 70% (tái dùng chuẩn chung)
        // thì chốt writingCurrentLevel để mở khóa Daily (mirror WritingTestService)
        TestLevelEvaluationUtil.LevelEvaluation evaluation =
                TestLevelEvaluationUtil.evaluateSafeDivision(totalScore, maxScore, request.getTestedLevel());
        if (evaluation.isPassed()) {
            user.setWritingCurrentLevel(request.getTestedLevel());
            user.setLastWritingPlacementDate(LocalDate.now());
            userRepository.save(user);
        }

        record.setScore(totalScore);
        record.setStatus(TestRecordStatus.COMPLETED);
        try {
            record.setReviewJson(objectMapper.writeValueAsString(reviewList));
        } catch (Exception ignored) {
        }
        recordRepository.save(record);

        return new WritingPart23ResultDto(frozen.size(), totalScore, maxScore,
                p1Score, p2Score, p3Score, reviewList, true, 0,
                evaluation.getRecommendedLevel(), evaluation.isPassed());
    }

    private int gradePart1(List<WritingPart23QuestionDto> p1Dtos, Map<Long, String> answerMap,
            User user, List<QuestionReviewDto> reviewList) {
        List<Long> ids = p1Dtos.stream().map(WritingPart23QuestionDto::getQuestionId).toList();
        List<WritingQuestion> questions = questionRepository.findAllById(ids);
        Map<Long, WritingQuestion> qMap = questions.stream()
                .collect(Collectors.toMap(WritingQuestion::getId, q -> q, (a, b) -> a));

        List<Map<String, Object>> aiInput = new ArrayList<>();
        List<String> imageUrls = new ArrayList<>();
        for (WritingPart23QuestionDto d : p1Dtos) {
            WritingQuestion q = qMap.get(d.getQuestionId());
            if (q == null) {
                continue;
            }
            Map<String, Object> m = new HashMap<>();
            m.put("questionId", q.getId());
            m.put("userSentence", answerMap.getOrDefault(d.getQuestionId(), ""));
            m.put("givenWords", q.getGivenWords());
            m.put("requiredGrammar", null);
            aiInput.add(m);
            imageUrls.add(q.getImageUrl());
        }

        List<WritingAiGradingResponseDto> results = gradePart1WithAi(aiInput, imageUrls);
        int sum = 0;
        for (WritingAiGradingResponseDto r : results) {
            int score = Math.max(0, Math.min(3, r.getScore() == null ? 0 : r.getScore()));
            sum += score;
            if (r.getWeakness() != null && r.getWeakness().getKnowledgeType() != null) {
                recordWeaknessToSM2(user, r.getWeakness(), ToeicPart.WRITING_PART_1);
            }
            QuestionReviewDto review = new QuestionReviewDto();
            review.setQuestionId(r.getQuestionId());
            review.setUserSelectedAnswer(answerMap.getOrDefault(r.getQuestionId(), ""));
            review.setCorrectAnswer("Điểm: " + score + "/3");
            review.setExplanation(r.getFeedback());
            review.setCorrect(score == 3);
            if (r.getWeakness() != null) {
                review.setKnowledgeName(r.getWeakness().getKnowledgeName());
            }
            reviewList.add(review);
        }
        return sum;
    }

    int gradePart2(WritingPart23QuestionDto dto, String answer, List<String> required,
            User user, List<QuestionReviewDto> reviewList) {
        PureGrade g = gradePart2Pure(dto, answer, required);
        for (KnowledgeType t : g.weaknesses) {
            progressRepository.save(gradingHelper.newWeaknessProgress(
                    user, gradingHelper.getOrCreateKnowledgeItem(t), ToeicPart.WRITING_PART_2));
        }
        QuestionReviewDto review = new QuestionReviewDto();
        review.setQuestionId(dto.getQuestionId());
        review.setUserSelectedAnswer(answer);
        review.setCorrectAnswer("Điểm: " + g.finalScore + "/4");
        review.setExplanation(g.feedbackWithNote);
        review.setCorrect(g.finalScore == 4);
        if (!g.weaknesses.isEmpty()) {
            review.setKnowledgeName(g.weaknesses.get(0).name());
        }
        reviewList.add(review);
        return g.finalScore;
    }

    int gradePart3(WritingPart23QuestionDto dto, String answer, List<String> required,
            User user, List<QuestionReviewDto> reviewList) {
        PureGrade g = gradePart3Pure(dto, answer, required);
        for (KnowledgeType t : g.weaknesses) {
            progressRepository.save(gradingHelper.newWeaknessProgress(
                    user, gradingHelper.getOrCreateKnowledgeItem(t), ToeicPart.WRITING_PART_3));
        }
        QuestionReviewDto review = new QuestionReviewDto();
        review.setQuestionId(dto.getQuestionId());
        review.setUserSelectedAnswer(answer);
        review.setCorrectAnswer("Điểm: " + g.finalScore + "/5");
        review.setExplanation(g.feedbackWithNote
                + (g.wordCount != null ? " (Số từ AI đếm: " + g.wordCount + ")" : ""));
        review.setCorrect(g.finalScore == 5);
        if (!g.weaknesses.isEmpty()) {
            review.setKnowledgeName(g.weaknesses.get(0).name());
        }
        reviewList.add(review);
        return g.finalScore;
    }

    /** Kết quả chấm thuần (không persist) để Daily tái dùng mà không ghi đè SM-2 của Test. */
    public static class PureGrade {
        public int finalScore;
        public String feedbackWithNote;
        public List<KnowledgeType> weaknesses = List.of();
        public Map<String, Boolean> passes = Map.of();
        public Integer wordCount;
    }

    public PureGrade gradePart2Pure(WritingPart23QuestionDto dto, String answer, List<String> required) {
        List<String> req = required != null ? required : List.of();
        String prompt = AIPromptTemplates.buildWritingPart2GradingPrompt()
                + "\n\n- Email gốc: " + safe(dto.getEmailBody())
                + "\n- Yêu cầu (Directions): " + safe(dto.getDirections())
                + "\n- Bài làm của User: " + safe(answer)
                + "\nRÀNG BUỘC NGỮ PHÁP BẮT BUỘC HÔM NAY: " + toJsonArray(req);
        WritingPart23AiResultDto ai = gradingHelper.parseAiResult(callAiWithRetry(prompt, true));
        PureGrade g = new PureGrade();
        g.finalScore = gradingHelper.applyPenalty(ai, req, 4, false);
        g.feedbackWithNote = (ai != null && ai.getFeedback() != null ? ai.getFeedback() : "")
                + gradingHelper.buildPenaltyNote(ai, req, false);
        g.weaknesses = gradingHelper.filterWeaknesses(ai, req, 2);
        g.passes = passesOf(ai, req);
        return g;
    }

    public PureGrade gradePart3Pure(WritingPart23QuestionDto dto, String answer, List<String> required) {
        List<String> req = required != null ? required : List.of();
        String prompt = AIPromptTemplates.buildWritingPart3GradingPrompt()
                + "\n\n- Đề bài: " + safe(dto.getEssayQuestion())
                + "\n- Bài làm của User: " + safe(answer)
                + "\nRÀNG BUỘC NGỮ PHÁP BẮT BUỘC HÔM NAY: " + toJsonArray(req);
        WritingPart23AiResultDto ai = gradingHelper.parseAiResult(callAiWithRetry(prompt, false));
        PureGrade g = new PureGrade();
        g.finalScore = gradingHelper.applyPenalty(ai, req, 5, true);
        g.feedbackWithNote = (ai != null && ai.getFeedback() != null ? ai.getFeedback() : "")
                + gradingHelper.buildPenaltyNote(ai, req, true);
        g.weaknesses = gradingHelper.filterWeaknesses(ai, req, 5);
        g.passes = passesOf(ai, req);
        g.wordCount = ai != null ? ai.getWordCount() : null;
        return g;
    }

    private Map<String, Boolean> passesOf(WritingPart23AiResultDto ai, List<String> required) {
        Map<String, Boolean> results = ai != null ? ai.getConstraintResults() : null;
        Map<String, Boolean> passes = new HashMap<>();
        for (String req : required) {
            passes.put(req, Boolean.TRUE.equals(results != null ? results.get(req) : null));
        }
        return passes;
    }

    private List<WritingAiGradingResponseDto> gradePart1WithAi(
            List<Map<String, Object>> aiInput, List<String> imageUrls) {
        if (aiInput.isEmpty()) {
            return List.of();
        }
        final String[] holder = new String[1];
        try {
            String finalPrompt = AIPromptTemplates.buildWritingPart1TestGradingPrompt()
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
            }, "AI Chấm điểm Writing P1 (session hỗn hợp)", MAX_GRADING_RETRIES,
                    GRADING_RETRY_DELAY_MS, GRADING_RETRY_BACKOFF_MS);
        } catch (Exception e) {
            log.error("Lỗi chấm P1 session hỗn hợp: ", e);
            throw new RuntimeException("Hệ thống chấm thi AI đang quá tải, vui lòng thử lại sau.");
        }
        // Guard null: RetryExecutor nuốt lỗi sau khi bỏ cuộc nên holder có thể null —
        // bắt buộc kiểm tra trước khi parse, nếu không readValue(null) crash 500 IllegalArgumentException
        if (holder[0] == null || holder[0].isBlank()) {
            throw new QuotaExceededException(
                    "Hệ thống AI chấm bài đang quá tải (hết quota), vui lòng đợi ít phút rồi nộp lại.");
        }
        try {
            return objectMapper.readValue(holder[0], new TypeReference<List<WritingAiGradingResponseDto>>() {});
        } catch (JsonProcessingException e) {
            log.error("Lỗi parse JSON P1 từ AI", e);
            throw new RuntimeException("AI trả về kết quả lỗi định dạng, vui lòng thử lại.");
        }
    }

    private String callAiWithRetry(String prompt, boolean isPart2) {
        final String[] holder = new String[1];
        try {
            RetryExecutor.executeWithRetry(() -> {
                String result;
                try {
                    // Chấm P2/P3 có xoay model/key khi dính 429, ném lỗi để retry ngoài thử lại
                    result = isPart2
                            ? aiService.gradeWritingPart2Resilient(prompt)
                            : aiService.gradeWritingPart3Resilient(prompt);
                } catch (Exception e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
                if (result == null || result.trim().isEmpty()) {
                    throw new RuntimeException("AI trả về rỗng, cần thử lại.");
                }
                holder[0] = result;
            }, "AI Chấm điểm Writing " + (isPart2 ? "Part 2" : "Part 3"),
                    MAX_GRADING_RETRIES, GRADING_RETRY_DELAY_MS, GRADING_RETRY_BACKOFF_MS);
        } catch (Exception e) {
            log.error("Lỗi chấm {} session hỗn hợp: ", isPart2 ? "P2" : "P3", e);
            throw new RuntimeException("Hệ thống chấm thi AI đang quá tải, vui lòng thử lại sau.");
        }
        // Guard null: RetryExecutor nuốt lỗi sau khi bỏ cuộc — trả 429 rõ ràng thay vì
        // để null lọt xuống parse/chấm 0 điểm trong lặng lẽ
        if (holder[0] == null || holder[0].isBlank()) {
            throw new QuotaExceededException(
                    "Hệ thống AI chấm bài đang quá tải (hết quota), vui lòng đợi ít phút rồi nộp lại.");
        }
        return holder[0];
    }

    private void recordWeaknessToSM2(User user, WritingAiGradingResponseDto.Weakness weakness, ToeicPart part) {
        try {
            KnowledgeType type = KnowledgeType.valueOf(weakness.getKnowledgeType());
            KnowledgeItem kItem = knowledgeItemRepository.findAll().stream()
                    .filter(k -> k.getKnowledgeType() == type && k.getPurpose() == Purpose.PRACTICE)
                    .findFirst().orElseGet(() -> {
                        KnowledgeItem n = new KnowledgeItem();
                        n.setKnowledgeType(type);
                        n.setKnowledgeName(weakness.getKnowledgeName());
                        n.setPurpose(Purpose.PRACTICE);
                        return knowledgeItemRepository.save(n);
                    });
            UserLearningProgress progress = new UserLearningProgress();
            progress.setUser(user);
            progress.setKnowledgeItem(kItem);
            progress.setToeicPart(part);
            progress.setEaseFactor(2.5);
            progress.setIntervalDays(1);
            progress.setRepetitionCount(0);
            progress.setNextReviewDate(LocalDateTime.now().plusDays(1));
            progressRepository.save(progress);
        } catch (Exception e) {
            log.error("Lỗi khi lưu điểm yếu Writing vào SM2: ", e);
        }
    }

    private List<WritingPart23QuestionDto> readFrozen(WritingTestRecord record) {
        try {
            return objectMapper.readValue(record.getQuestionsJson(),
                    new TypeReference<List<WritingPart23QuestionDto>>() {});
        } catch (Exception e) {
            log.error("Lỗi parse khung đề hỗn hợp", e);
            throw new RuntimeException("Lỗi hệ thống khi tải đề thi.");
        }
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String toJsonArray(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list != null ? list : List.of());
        } catch (Exception e) {
            return "[]";
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
            log.error("Lỗi parse JSON session hỗn hợp", e);
            throw new RuntimeException("Lỗi hệ thống khi tải đề thi.");
        }
        return dto;
    }
}
