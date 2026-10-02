package com.longdq.adaptengbackend.modules.writing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.longdq.adaptengbackend.common.ai.AIPromptTemplates;
import com.longdq.adaptengbackend.common.util.RetryExecutor;
import com.longdq.adaptengbackend.common.util.TestLevelEvaluationUtil;
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
import com.longdq.adaptengbackend.common.ai.AIService;
import com.longdq.adaptengbackend.common.dto.QuestionReviewDto;
import com.longdq.adaptengbackend.common.dto.TestSubmissionRequestDto;
import com.longdq.adaptengbackend.common.dto.TestSubmissionResponseDto;
import com.longdq.adaptengbackend.common.dto.UserAnswerDto;
import com.longdq.adaptengbackend.common.enums.KnowledgeType;
import com.longdq.adaptengbackend.common.enums.Level;
import com.longdq.adaptengbackend.common.enums.Purpose;
import com.longdq.adaptengbackend.common.enums.TestRecordStatus;
import com.longdq.adaptengbackend.common.enums.ToeicPart;
import com.longdq.adaptengbackend.common.exception.QuotaExceededException;
import com.longdq.adaptengbackend.modules.spacedrepetition.entity.KnowledgeItem;
import com.longdq.adaptengbackend.modules.spacedrepetition.entity.UserLearningProgress;
import com.longdq.adaptengbackend.modules.spacedrepetition.repository.KnowledgeItemRepository;
import com.longdq.adaptengbackend.modules.spacedrepetition.repository.UserLearningProgressRepository;
import com.longdq.adaptengbackend.modules.user.entity.User;
import com.longdq.adaptengbackend.modules.user.repository.UserRepository;
import com.longdq.adaptengbackend.modules.writing.dto.WritingAiGradingResponseDto;
import com.longdq.adaptengbackend.modules.writing.dto.WritingQuestionDto;
import com.longdq.adaptengbackend.modules.writing.dto.WritingTestResponseDto;
import com.longdq.adaptengbackend.modules.writing.entity.WritingQuestion;
import com.longdq.adaptengbackend.modules.writing.entity.WritingTestRecord;
import com.longdq.adaptengbackend.modules.writing.repository.WritingQuestionRepository;
import com.longdq.adaptengbackend.modules.writing.repository.WritingTestRecordRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class WritingTestService {

    private final WritingQuestionRepository questionRepository;
    private final WritingTestRecordRepository recordRepository;
    private final UserRepository userRepository;
    private final KnowledgeItemRepository knowledgeItemRepository;
    private final UserLearningProgressRepository progressRepository;
    private final AIService aiService;
    private final ObjectMapper objectMapper;

    private static final int MAX_GRADING_RETRIES = 3;
    private static final long GRADING_RETRY_DELAY_MS = 10000;
    private static final long GRADING_RETRY_BACKOFF_MS = 2000;

    @Transactional
    public WritingTestResponseDto generatePlacementTest(UUID userId, Level requestedLevel) {
        Optional<WritingTestRecord> existingRecord = recordRepository.findByUserIdAndTestTypeAndStatus(
                userId, "PLACEMENT_TEST", TestRecordStatus.IN_PROGRESS);

        if (existingRecord.isPresent()) {
            if (existingRecord.get().getLevel() == requestedLevel) {
                log.info("Trả về bài thi Placement Test ({}) đang làm dở cho user: {}", requestedLevel, userId);
                return buildResponseDto(existingRecord.get());
            } else {
                WritingTestRecord oldRecord = existingRecord.get();
                oldRecord.setStatus(TestRecordStatus.EXPIRED);
                recordRepository.save(oldRecord);
            }
        }

        // ĐỔI 5 THÀNH 3 CÂU THEO YÊU CẦU CỦA BẠN
        List<WritingQuestion> questions = questionRepository.findRandomByPartAndLevel(
                ToeicPart.WRITING_PART_1.name(), requestedLevel.name(), 3);

        if (questions.size() < 3) {
            throw new RuntimeException("Hệ thống chưa tạo đủ câu hỏi Writing Part 1 cho level " + requestedLevel.name() + ". Vui lòng thử lại sau!");
        }

        WritingTestRecord newRecord = new WritingTestRecord();
        newRecord.setUserId(userId);
        newRecord.setTestType("PLACEMENT_TEST");
        newRecord.setLevel(requestedLevel);
        newRecord.setStatus(TestRecordStatus.IN_PROGRESS);
        newRecord.setTestDate(LocalDate.now());
        newRecord.setTotalQuestions(3); // ĐỔI THÀNH 3

        List<WritingQuestionDto> questionDtos = questions.stream().map(this::convertToDto).collect(Collectors.toList());

        try {
            newRecord.setQuestionsJson(objectMapper.writeValueAsString(questionDtos));
        } catch (JsonProcessingException e) {
            log.error("Lỗi parse câu hỏi thành JSON", e);
            throw new RuntimeException("Lỗi hệ thống khi tạo đề thi.");
        }

        newRecord = recordRepository.save(newRecord);
        log.info("Đã tạo mới bài thi Placement Test ({}) cho user: {}", requestedLevel, userId);

        return buildResponseDto(newRecord);
    }

    @Transactional
    public TestSubmissionResponseDto submitPlacementTest(TestSubmissionRequestDto request, User user) {
        WritingTestRecord record = recordRepository.findByUserIdAndTestTypeAndStatus(
                        user.getId(), "PLACEMENT_TEST", TestRecordStatus.IN_PROGRESS)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bài thi đầu vào nào đang diễn ra!"));

        List<Long> questionIds = request.getAnswers().stream()
                .map(UserAnswerDto::getQuestionId).toList();
        List<WritingQuestion> questions = questionRepository.findAllById(questionIds);

        List<Map<String, Object>> aiInputData = new ArrayList<>();
        List<String> imageUrlsToDownload = new ArrayList<>();

        for (UserAnswerDto answer : request.getAnswers()) {
            WritingQuestion q = questions.stream().filter(x -> x.getId().equals(answer.getQuestionId())).findFirst().orElse(null);
            if (q != null) {
                Map<String, Object> inputMap = new HashMap<>();
                inputMap.put("questionId", q.getId());
                inputMap.put("userSentence", answer.getSelectedAnswer());
                inputMap.put("givenWords", q.getGivenWords());

                // MẸO: CỐ TÌNH SET NULL ĐỂ ÉP AI PHẢI "CHẤM TỰ DO" (VÌ ĐÂY LÀ TEST)
                inputMap.put("requiredGrammar", null);

                aiInputData.add(inputMap);
                imageUrlsToDownload.add(q.getImageUrl());
            }
        }

        final String[] aiResponseJsonHolder = new String[1];

        try {
            String gradingPrompt = AIPromptTemplates.buildWritingPart1TestGradingPrompt();
            String finalPrompt = gradingPrompt + "\n\nĐây là inputData của thí sinh:\n"
                    + objectMapper.writeValueAsString(aiInputData);

            RetryExecutor.executeWithRetry(
                    () -> {
                        String result;
                        try {
                            // Chấm P1 có xoay model/key khi dính 429, ném lỗi để retry ngoài thử lại
                            result = aiService.gradeWritingPart1Resilient(
                                    finalPrompt, imageUrlsToDownload);
                        } catch (Exception ex) {
                            throw new RuntimeException(ex.getMessage(), ex);
                        }
                        if (result == null || result.trim().isEmpty()) {
                            throw new RuntimeException("AI trả về kết quả rỗng hoặc null, cần thử lại.");
                        }
                        aiResponseJsonHolder[0] = result;
                    },
                    "AI Chấm điểm Writing Part 1",
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

        List<WritingAiGradingResponseDto> aiResults;
        try {
            aiResults = objectMapper.readValue(aiResponseJson, new TypeReference<List<WritingAiGradingResponseDto>>() {});
        } catch (JsonProcessingException e) {
            log.error("Lỗi parse JSON từ AI", e);
            throw new RuntimeException("AI trả về kết quả lỗi định dạng, vui lòng thử lại.");
        }

        int totalScore = 0;
        int maxScore = questions.size() * 3; // 3 câu * 3 điểm = 9 điểm tối đa
        List<QuestionReviewDto> reviewList = new ArrayList<>();

        for (WritingAiGradingResponseDto aiResult : aiResults) {
            totalScore += aiResult.getScore();

            if (aiResult.getWeakness() != null && aiResult.getWeakness().getKnowledgeType() != null) {
                recordWeaknessToSM2(user, aiResult.getWeakness());
            }

            QuestionReviewDto reviewDto = new QuestionReviewDto();
            reviewDto.setQuestionId(aiResult.getQuestionId());

            String userAnswer = request.getAnswers().stream()
                    .filter(a -> a.getQuestionId().equals(aiResult.getQuestionId()))
                    .map(UserAnswerDto::getSelectedAnswer)
                    .findFirst().orElse("");
            reviewDto.setUserSelectedAnswer(userAnswer);
            reviewDto.setCorrectAnswer("Điểm: " + aiResult.getScore() + "/3");
            reviewDto.setExplanation(aiResult.getFeedback());
            reviewDto.setCorrect(aiResult.getScore() == 3);

            if (aiResult.getWeakness() != null) {
                reviewDto.setKnowledgeName(aiResult.getWeakness().getKnowledgeName());
            }
            reviewList.add(reviewDto);
        }

        TestLevelEvaluationUtil.LevelEvaluation evaluation =
                TestLevelEvaluationUtil.evaluateSafeDivision(totalScore, maxScore, request.getTestedLevel());

        if (evaluation.isPassed()) {
            user.setWritingCurrentLevel(request.getTestedLevel());
            user.setLastWritingPlacementDate(LocalDate.now());
            userRepository.save(user);
        }

        record.setScore(totalScore);
        record.setStatus(TestRecordStatus.COMPLETED);
        record.setAchievedLevel(evaluation.isPassed() ? request.getTestedLevel() : evaluation.getRecommendedLevel());
        try {
            record.setReviewJson(objectMapper.writeValueAsString(reviewList));
        } catch (Exception ignored) {}
        recordRepository.save(record);

        TestSubmissionResponseDto response = new TestSubmissionResponseDto();
        response.setTotalQuestions(questions.size());
        response.setCorrectAnswers(totalScore);
        response.setScorePercentage(evaluation.getScorePercentage());
        response.setPassedThreshold(evaluation.isPassed());
        response.setTestedLevel(request.getTestedLevel());
        response.setRecommendedLevel(evaluation.getRecommendedLevel());
        response.setReviewList(reviewList);

        return response;
    }

    @Transactional
    public void setWritingLevel(UUID userId, Level newLevel) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        user.setWritingCurrentLevel(newLevel);
        userRepository.save(user);

        log.info("Đã cập nhật writingCurrentLevel thành {} cho user ID: {}", newLevel, userId);
    }

    private void recordWeaknessToSM2(User user, WritingAiGradingResponseDto.Weakness weakness) {
        try {
            KnowledgeType type = KnowledgeType.valueOf(weakness.getKnowledgeType());

            KnowledgeItem kItem = knowledgeItemRepository.findAll().stream()
                    .filter(k -> k.getKnowledgeType() == type && k.getPurpose() == Purpose.PRACTICE)
                    .findFirst().orElseGet(() -> {
                        KnowledgeItem newKi = new KnowledgeItem();
                        newKi.setKnowledgeType(type);
                        newKi.setKnowledgeName(weakness.getKnowledgeName());
                        newKi.setPurpose(Purpose.PRACTICE);
                        return knowledgeItemRepository.save(newKi);
                    });

            UserLearningProgress progress = new UserLearningProgress();
            progress.setUser(user);
            progress.setKnowledgeItem(kItem);
            progress.setToeicPart(ToeicPart.WRITING_PART_1);
            progress.setEaseFactor(2.5);
            progress.setIntervalDays(1);
            progress.setRepetitionCount(0);
            progress.setNextReviewDate(LocalDateTime.now().plusDays(1));
            progressRepository.save(progress);

        } catch (Exception e) {
            log.error("Lỗi khi lưu điểm yếu Writing vào SM2: ", e);
        }
    }

    private WritingQuestionDto convertToDto(WritingQuestion q) {
        WritingQuestionDto dto = new WritingQuestionDto();
        dto.setQuestionId(q.getId());
        dto.setImageUrl(q.getImageUrl());
        dto.setGivenWords(q.getGivenWords());

        // CẬP NHẬT LẠI DTO: Lấy từ KnowledgeItem
        if (q.getKnowledgeItem() != null) {
            dto.setKnowledgeItemId(q.getKnowledgeItem().getId());
            dto.setRequiredGrammar(q.getKnowledgeItem().getKnowledgeName());
        }

        return dto;
    }

    private WritingTestResponseDto buildResponseDto(WritingTestRecord record) {
        WritingTestResponseDto response = new WritingTestResponseDto();
        response.setRecordId(record.getId());
        response.setStatus(record.getStatus().name());
        response.setTestType(record.getTestType());

        try {
            List<WritingQuestionDto> questions = objectMapper.readValue(
                    record.getQuestionsJson(),
                    new TypeReference<List<WritingQuestionDto>>() {}
            );
            response.setQuestions(questions);

            if (record.getUserAnswersJson() != null && !record.getUserAnswersJson().isEmpty()) {
                Map<String, String> answers = objectMapper.readValue(
                        record.getUserAnswersJson(),
                        new TypeReference<Map<String, String>>() {}
                );
                response.setSavedAnswers(answers);
            }
        } catch (JsonProcessingException e) {
            log.error("Lỗi parse JSON", e);
            throw new RuntimeException("Lỗi hệ thống khi tải đề thi.");
        }

        return response;
    }
}