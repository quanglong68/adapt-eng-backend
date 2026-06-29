package com.longdq.adaptengbackend.service;

import com.longdq.adaptengbackend.dto.*;
import com.longdq.adaptengbackend.entity.LevelPromotionConfig;
import com.longdq.adaptengbackend.entity.Passage;
import com.longdq.adaptengbackend.entity.Question;
import com.longdq.adaptengbackend.entity.User;
import com.longdq.adaptengbackend.entity.UserLearningProgress;
import com.longdq.adaptengbackend.enums.Level;
import com.longdq.adaptengbackend.enums.ToeicPart;
import com.longdq.adaptengbackend.repository.LevelPromotionConfigRepository;
import com.longdq.adaptengbackend.repository.PassageRepository;
import com.longdq.adaptengbackend.repository.QuestionRepository;
import com.longdq.adaptengbackend.repository.UserLearningProgressRepository;
import com.longdq.adaptengbackend.repository.UserRepository;
import com.longdq.adaptengbackend.util.KnowledgeDisplayNameUtil;
import com.longdq.adaptengbackend.util.QuestionReviewBuilder;
import com.longdq.adaptengbackend.util.SecurityUtils;
import com.longdq.adaptengbackend.util.TestLevelEvaluationUtil;
import com.longdq.adaptengbackend.util.TestProgressDebtHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ToeicTestService {

    private final QuestionRepository questionRepository;
    private final PassageRepository passageRepository;
    private final UserLearningProgressRepository progressRepository;
    private final UserRepository userRepository;
    private final LevelPromotionConfigRepository promotionConfigRepo;

    public List<ToeicPassageResponseDto> getToeicTestQuestions(Level level) {
        List<ToeicPassageResponseDto> finalResponseBlocks = new ArrayList<>();

        List<Question> part5Questions = questionRepository.findRandomToeicQuestions(
                ToeicPart.PART_5.name(), level.name(), 15);
        if (!part5Questions.isEmpty()) {
            List<ToeicQuestionResponseDto> p5QuestionDtos = part5Questions.stream()
                    .map(q -> new ToeicQuestionResponseDto(q.getId(), q.getContent(), q.getOptions(), q.getQuestionType()))
                    .collect(Collectors.toList());
            finalResponseBlocks.add(new ToeicPassageResponseDto(null, null, ToeicPart.PART_5, p5QuestionDtos));
        }

        fetchAndGroupToeicPassages(ToeicPart.PART_6, level, 2, finalResponseBlocks);
        fetchAndGroupToeicPassages(ToeicPart.PART_7_SINGLE, level, 3, finalResponseBlocks);
        fetchAndGroupToeicPassages(ToeicPart.PART_7_MULTIPLE, level, 3, finalResponseBlocks);

        return finalResponseBlocks;
    }

    private void fetchAndGroupToeicPassages(
            ToeicPart part, Level level, int limit, List<ToeicPassageResponseDto> responseBlocks) {
        List<Passage> passages = passageRepository.findRandomToeicPassages(part.name(), level.name(), limit);
        for (Passage passage : passages) {
            List<ToeicQuestionResponseDto> questionDtos = passage.getQuestions().stream()
                    .map(q -> new ToeicQuestionResponseDto(q.getId(), q.getContent(), q.getOptions(), q.getQuestionType()))
                    .collect(Collectors.toList());
            responseBlocks.add(new ToeicPassageResponseDto(
                    passage.getId(), passage.getContent(), part, questionDtos));
        }
    }

    // ==============================================================
    // LUỒNG NỘP BÀI TEST BÌNH THƯỜNG (ĐÁNH GIÁ NĂNG LỰC)
    // ==============================================================
    @Transactional
    public TestSubmissionResponseDto submitToeicTest(TestSubmissionRequestDto request) {
        User user = SecurityUtils.getCurrentUser();

        GradingResult result = gradeTestAnswers(request, user);
        int totalQuestions = request.getAnswers().size();

        TestLevelEvaluationUtil.LevelEvaluation evaluation =
                TestLevelEvaluationUtil.evaluateSafeDivision(result.correctCount, totalQuestions, request.getTestedLevel());

        TestSubmissionResponseDto response = new TestSubmissionResponseDto();
        response.setTotalQuestions(totalQuestions);
        response.setCorrectAnswers(result.correctCount);
        response.setScorePercentage(evaluation.getScorePercentage());
        response.setPassedThreshold(evaluation.isPassed());
        response.setTestedLevel(request.getTestedLevel());
        response.setRecommendedLevel(evaluation.getRecommendedLevel());
        response.setReviewList(result.reviewList);

        return response;
    }

    // ==============================================================
    // LUỒNG NỘP BÀI TEST THĂNG CẤP (BOSS FIGHT)
    // ==============================================================
    @Transactional
    public TestSubmissionResponseDto submitLevelUpTest(TestSubmissionRequestDto request) {
        User user = SecurityUtils.getCurrentUser();

        GradingResult result = gradeTestAnswers(request, user);
        int totalQuestions = request.getAnswers().size();

        int scorePercent = totalQuestions > 0 ? (result.correctCount * 100) / totalQuestions : 0;

        LevelPromotionConfig config = promotionConfigRepo.findById(request.getTestedLevel())
                .orElseThrow(() -> new RuntimeException("Thiếu config thăng cấp trong DB cho level: " + request.getTestedLevel()));

        TestSubmissionResponseDto response = new TestSubmissionResponseDto();
        response.setTotalQuestions(totalQuestions);
        response.setCorrectAnswers(result.correctCount);
        response.setScorePercentage(scorePercent);
        response.setTestedLevel(request.getTestedLevel());
        response.setRecommendedLevel(null);
        response.setReviewList(result.reviewList);

        if (scorePercent >= config.getPassingScorePercent()) {
            // WIN BOSS
            user.setCurrentLevel(request.getTestedLevel());
            user.setLastLevelUpTestDate(null);
            response.setPassedThreshold(true);
        } else {
            // LOSE BOSS
            user.setLastLevelUpTestDate(LocalDate.now());
            response.setPassedThreshold(false);
        }

        userRepository.save(user);
        return response;
    }

    // ==============================================================
    // CÁC HÀM HELPER DÙNG CHUNG
    // ==============================================================

    private static class GradingResult {
        int correctCount;
        List<QuestionReviewDto> reviewList;

        public GradingResult(int correctCount, List<QuestionReviewDto> reviewList) {
            this.correctCount = correctCount;
            this.reviewList = reviewList;
        }
    }

    private GradingResult gradeTestAnswers(TestSubmissionRequestDto request, User user) {
        List<QuestionReviewDto> reviewList = new ArrayList<>();
        Map<String, UserLearningProgress> progressCache = new HashMap<>();

        int correctCount = 0;

        List<Long> questionIds = request.getAnswers().stream()
                .map(UserAnswerDto::getQuestionId)
                .toList();
        Map<Long, Question> questionMap = questionRepository.findAllById(questionIds).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));

        for (UserAnswerDto answer : request.getAnswers()) {
            Question question = questionMap.get(answer.getQuestionId());
            if (question == null) {
                continue;
            }

            boolean isCorrect = question.getCorrectAnswer().equalsIgnoreCase(answer.getSelectedAnswer());
            if (isCorrect) {
                correctCount++;
            } else {
                TestProgressDebtHelper.recordWrongAnswerDebt(
                        question, user, progressCache, progressRepository, question.getToeicPart());
            }

            reviewList.add(QuestionReviewBuilder.build(
                    question,
                    answer.getSelectedAnswer(),
                    isCorrect,
                    KnowledgeDisplayNameUtil.forToeicTest(question)));
        }

        progressRepository.saveAll(progressCache.values());
        return new GradingResult(correctCount, reviewList);
    }
}