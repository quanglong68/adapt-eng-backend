//package com.longdq.adaptengbackend.service;
//
//import com.longdq.adaptengbackend.dto.DailyReviewResultResponseDto;
//import com.longdq.adaptengbackend.dto.DailyReviewSubmissionRequestDto;
//import com.longdq.adaptengbackend.dto.QuestionResponseDto;
//import com.longdq.adaptengbackend.dto.QuestionReviewDto;
//import com.longdq.adaptengbackend.dto.UserAnswerDto;
//import com.longdq.adaptengbackend.entity.Question;
//import com.longdq.adaptengbackend.entity.User;
//import com.longdq.adaptengbackend.entity.UserLearningProgress;
//import com.longdq.adaptengbackend.entity.UserQuestionHistory;
//import com.longdq.adaptengbackend.repository.QuestionRepository;
//import com.longdq.adaptengbackend.repository.UserLearningProgressRepository;
//import com.longdq.adaptengbackend.repository.UserQuestionHistoryRepository;
//import com.longdq.adaptengbackend.repository.UserRepository;
//import com.longdq.adaptengbackend.util.DailyReviewProgressHelper;
//import com.longdq.adaptengbackend.util.KnowledgeDisplayNameUtil;
//import com.longdq.adaptengbackend.util.QuestionReviewBuilder;
//import com.longdq.adaptengbackend.util.SecurityUtils;
//import lombok.RequiredArgsConstructor;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.LocalDateTime;
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.Optional;
//import java.util.stream.Collectors;
//
//@Service
//@RequiredArgsConstructor
//public class PracticeService {
//
//    private static final int MAX_DAILY_QUESTIONS = 30;
//
//    private final UserLearningProgressRepository progressRepository;
//    private final QuestionRepository questionRepository;
//    private final UserQuestionHistoryRepository userQuestionHistoryRepository;
//    private final SpacedRepetitionService spacedRepetitionService;
//    private final UserRepository userRepository;
//
//    public List<QuestionResponseDto> generateDailyReviewTest() {
//        User user = SecurityUtils.getCurrentUser();
//
//        List<Question> finalTestQuestions = new ArrayList<>();
//        List<Long> pickedQuestionIds = new ArrayList<>();
//        pickedQuestionIds.add(-1L);
//
//        List<UserLearningProgress> dueItems = progressRepository
//                .findByUserIdAndNextReviewDateLessThanEqualOrderByIntervalDaysAscNextReviewDateAsc(
//                        user.getId(), LocalDateTime.now());
//
//        for (UserLearningProgress item : dueItems) {
//            if (finalTestQuestions.size() >= MAX_DAILY_QUESTIONS) {
//                break;
//            }
//
//            Optional<Question> questionOpt = findReviewQuestion(item, user.getId(), pickedQuestionIds);
//            if (questionOpt.isEmpty()) {
//                continue;
//            }
//
//            Question question = questionOpt.get();
//            finalTestQuestions.add(question);
//            pickedQuestionIds.add(question.getId());
//        }
//
//        int missingCount = MAX_DAILY_QUESTIONS - finalTestQuestions.size();
//        if (missingCount > 0) {
//            List<Question> fillQuestions = questionRepository.findRandomQuestionsToFill(
//                    user.getCurrentLevel().name(), user.getId(), pickedQuestionIds, missingCount);
//            finalTestQuestions.addAll(fillQuestions);
//        }
//
//        Collections.shuffle(finalTestQuestions);
//
//        return finalTestQuestions.stream()
//                .map(q -> new QuestionResponseDto(q.getId(), q.getContent(), q.getOptions(), q.getQuestionType()))
//                .collect(Collectors.toList());
//    }
//
//    @Transactional
//    public DailyReviewResultResponseDto submitDailyReview(DailyReviewSubmissionRequestDto request) {
//        User user = SecurityUtils.getCurrentUser();
//        LocalDateTime now = LocalDateTime.now();
//
//        List<Long> questionIds = request.getAnswers().stream()
//                .map(UserAnswerDto::getQuestionId)
//                .collect(Collectors.toList());
//
//        Map<Long, Question> questionMap = questionRepository.findAllById(questionIds).stream()
//                .collect(Collectors.toMap(Question::getId, q -> q));
//
//        List<UserQuestionHistory> historiesToSave = new ArrayList<>();
//        List<QuestionReviewDto> results = new ArrayList<>();
//        Map<String, UserLearningProgress> progressCache = new HashMap<>();
//
//        int correctCount = 0;
//        int totalEarnedXp = 0;
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
//            }
//
//            totalEarnedXp += isCorrect ? 10 : 2;
//
//            UserQuestionHistory history = new UserQuestionHistory();
//            history.setUserId(user.getId());
//            history.setQuestionId(question.getId());
//            history.setCorrect(isCorrect);
//            history.setAnsweredAt(now);
//            historiesToSave.add(history);
//
//            UserLearningProgress progress = DailyReviewProgressHelper.resolveOrCreateProgress(
//                    user, question, progressCache, progressRepository);
//            spacedRepetitionService.updateProgress(progress, isCorrect);
//
//            results.add(QuestionReviewBuilder.build(
//                    question,
//                    answer.getSelectedAnswer(),
//                    isCorrect,
//                    KnowledgeDisplayNameUtil.forDailyReview(question)));
//        }
//
//        userQuestionHistoryRepository.saveAll(historiesToSave);
//        progressRepository.saveAll(progressCache.values());
//        user.setTotalXp(user.getTotalXp() + totalEarnedXp);
//        userRepository.save(user);
//
//        return new DailyReviewResultResponseDto(request.getAnswers().size(), correctCount, results);
//    }
//
//    private Optional<Question> findReviewQuestion(
//            UserLearningProgress item, java.util.UUID userId, List<Long> pickedQuestionIds) {
//        java.util.UUID knowledgeId = item.getKnowledgeItem() != null ? item.getKnowledgeItem().getId() : null;
//        String targetWord = item.getTargetWord();
//
//        Optional<Question> questionOpt = questionRepository.findRandomQuestionForReview(
//                knowledgeId, targetWord, userId, pickedQuestionIds);
//
//        if (questionOpt.isPresent()) {
//            return questionOpt;
//        }
//
//        return questionRepository.findOldestAnsweredQuestionForReview(
//                knowledgeId, targetWord, userId, pickedQuestionIds);
//    }
//}
