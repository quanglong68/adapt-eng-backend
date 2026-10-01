package com.longdq.adaptengbackend.modules.legacy.service;

import com.longdq.adaptengbackend.modules.legacy.dto.QuestionResponseDto;
import com.longdq.adaptengbackend.common.dto.QuestionReviewDto;
import com.longdq.adaptengbackend.modules.user.dto.SetLevelRequestDto;
import com.longdq.adaptengbackend.common.dto.TestSubmissionRequestDto;
import com.longdq.adaptengbackend.common.dto.TestSubmissionResponseDto;
import com.longdq.adaptengbackend.common.dto.UserAnswerDto;
import com.longdq.adaptengbackend.modules.toeic.entity.Question;
import com.longdq.adaptengbackend.modules.user.entity.User;
import com.longdq.adaptengbackend.modules.spacedrepetition.entity.UserLearningProgress;
import com.longdq.adaptengbackend.common.enums.Level;
import com.longdq.adaptengbackend.modules.toeic.repository.QuestionRepository;
import com.longdq.adaptengbackend.modules.spacedrepetition.repository.UserLearningProgressRepository;
import com.longdq.adaptengbackend.modules.user.repository.UserRepository;
import com.longdq.adaptengbackend.common.util.KnowledgeDisplayNameUtil;
import com.longdq.adaptengbackend.common.util.QuestionReviewBuilder;
import com.longdq.adaptengbackend.common.security.SecurityUtils;
import com.longdq.adaptengbackend.common.util.TestLevelEvaluationUtil;
import com.longdq.adaptengbackend.common.util.TestProgressDebtHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
@Deprecated
public class TestService {

    private final QuestionRepository questionRepository;
    private final UserLearningProgressRepository userLearningProgressRepository;
    private final UserRepository userRepository;

    public List<QuestionResponseDto> getTestQuestions(Level level) {
        List<Question> questionList = questionRepository.find30RandomTestQuestionsByLevel(level);
        return questionList.stream()
                .map(q -> new QuestionResponseDto(q.getId(), q.getContent(), q.getOptions(), q.getQuestionType()))
                .toList();
    }

//    @Transactional
//    public TestSubmissionResponseDto submitTest(TestSubmissionRequestDto request) {
//        int totalQuestions = request.getAnswers().size();
//        int correctCount = 0;
//        User user = SecurityUtils.getCurrentUser();
//        List<QuestionReviewDto> reviewList = new ArrayList<>();
//        Map<String, UserLearningProgress> progressCache = new HashMap<>();
//
//        List<Long> questionIds = request.getAnswers().stream()
//                .map(UserAnswerDto::getQuestionId)
//                .toList();
//        Map<Long, Question> questionMap = questionRepository.findAllById(questionIds).stream()
//                .collect(Collectors.toMap(Question::getId, Function.identity()));
//
//        for (UserAnswerDto answer : request.getAnswers()) {
//            Question question = questionMap.get(answer.getQuestionId());
//            if (question == null) {
//                continue;
//            }
//
//            boolean isCorrect = question.getCorrectAnswer().equalsIgnoreCase(answer.getSelectedAnswer());
//            if (isCorrect) {
//                correctCount++;
//            } else {
//                TestProgressDebtHelper.recordWrongAnswerDebt(
//                        question, user, progressCache, userLearningProgressRepository);
//            }
//
//            reviewList.add(QuestionReviewBuilder.build(
//                    question,
//                    answer.getSelectedAnswer(),
//                    isCorrect,
//                    KnowledgeDisplayNameUtil.forGeneralEnglish(question)));
//        }
//
//        userLearningProgressRepository.saveAll(progressCache.values());
//
//        TestLevelEvaluationUtil.LevelEvaluation evaluation =
//                TestLevelEvaluationUtil.evaluateUnsafeDivision(correctCount, totalQuestions, request.getTestedLevel());
//
//        TestSubmissionResponseDto response = new TestSubmissionResponseDto();
//        response.setTotalQuestions(totalQuestions);
//        response.setCorrectAnswers(correctCount);
//        response.setScorePercentage(evaluation.getScorePercentage());
//        response.setPassedThreshold(evaluation.isPassed());
//        response.setTestedLevel(request.getTestedLevel());
//        response.setRecommendedLevel(evaluation.getRecommendedLevel());
//        response.setSystemMessage(evaluation.getSystemMessage());
//        response.setReviewList(reviewList);
//
//        return response;
//    }

    public void setUserCurrentLevel(SetLevelRequestDto request) {
        User user = SecurityUtils.getCurrentUser();
        user.setCurrentLevel(request.getSelectedLevel());
        userRepository.save(user);
    }
}
