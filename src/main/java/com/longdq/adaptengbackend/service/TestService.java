package com.longdq.adaptengbackend.service;

import com.longdq.adaptengbackend.dto.QuestionResponseDto;
import com.longdq.adaptengbackend.dto.SetLevelRequestDto;
import com.longdq.adaptengbackend.dto.TestSubmissionRequestDto;
import com.longdq.adaptengbackend.dto.TestSubmissionResponseDto;
import com.longdq.adaptengbackend.entity.KnowledgeItem;
import com.longdq.adaptengbackend.entity.Question;
import com.longdq.adaptengbackend.entity.User;
import com.longdq.adaptengbackend.entity.UserLearningProgress;
import com.longdq.adaptengbackend.enums.Level;
import com.longdq.adaptengbackend.repository.QuestionRepository;
import com.longdq.adaptengbackend.repository.UserLearningProgressRepository;
import com.longdq.adaptengbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class TestService {
    private final QuestionRepository questionRepository;
    private final UserLearningProgressRepository userLearningProgressRepository;
    private final UserRepository userRepository;
    public List<QuestionResponseDto> getTestQuestions(Level level){
        List<QuestionResponseDto> questions = new ArrayList<>();
        List<Question> questionList = questionRepository.find30RandomTestQuestionsByLevel(level);
        return questionList.stream().map(q ->  new QuestionResponseDto(q.getId(), q.getContent(),
                q.getOptions(), q.getQuestionType())).toList();
    }

    public TestSubmissionResponseDto submitTest(TestSubmissionRequestDto request) {
        int totalQuestions = request.getAnswers().size();
        int correctCount = 0;
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        List<TestSubmissionResponseDto.QuestionReviewDto> reviewList = new ArrayList<>();
        List<Question> questions = questionRepository.findAllById(request.getAnswers().stream().map(q -> q.getQuestionId()).toList());
        Map<Long, Question> questionMap = questions.stream()
                .collect(Collectors.toMap(
                        Question::getId,
                        Function.identity()
                ));
        Set<KnowledgeItem> failedKnowledgeItems = new java.util.HashSet<>();
        for (TestSubmissionRequestDto.UserAnswerDto answer : request.getAnswers()) {
            Question question = questionMap.get(answer.getQuestionId());

            boolean isCorrect = question.getCorrectAnswer().equals(answer.getSelectedAnswer());
            if (isCorrect) {
                correctCount++;
            } else {
                if (question.getKnowledgeItem() != null) {
                    failedKnowledgeItems.add(question.getKnowledgeItem());
                }
            }



            // Đổ data vào Review Dto
            TestSubmissionResponseDto.QuestionReviewDto review = new TestSubmissionResponseDto.QuestionReviewDto();
            review.setQuestionId(question.getId());
            review.setUserSelectedAnswer(answer.getSelectedAnswer());
            review.setCorrectAnswer(question.getCorrectAnswer());
            review.setCorrect(isCorrect);
            review.setExplanation(question.getExplanation());
            if (question.getKnowledgeItem() != null) {
                review.setKnowledgeName(question.getKnowledgeItem().getKnowledgeName());
            }
            reviewList.add(review);
        }

        for (KnowledgeItem knowledgeItem : failedKnowledgeItems) {
            UserLearningProgress progress = new UserLearningProgress();
            progress.setUser(user);
            progress.setKnowledgeItem(knowledgeItem);
            progress.setIntervalDays(0);
            progress.setRepetitionCount(0);
            progress.setEaseFactor(1.3);
            progress.setNextReviewDate(LocalDateTime.now().plusDays(1));
            userLearningProgressRepository.save(progress);
        }


// THUẬT TOÁN HẠ CẤP (DOWNGRADE LEVEL)
        double scorePercentage = (double) correctCount / totalQuestions * 100;
        boolean passed = scorePercentage >= 70.0; // Giả sử 70% là qua môn

        Level recommended = request.getTestedLevel();
        String message = "Tuyệt vời! Trình độ của bạn hoàn toàn phù hợp với mức " + recommended + ".";

        if (!passed) {
            int currentOrdinal = request.getTestedLevel().ordinal();
            if (currentOrdinal > 0) { // Nếu không phải là A1 thì lùi 1 cấp
                recommended = Level.values()[currentOrdinal - 1];
                message = "Bài test hơi quá sức. Điểm của bạn là " + String.format("%.1f", scorePercentage) +
                        "%. Chúng tôi khuyên bạn nên củng cố lại nền tảng ở mức " + recommended + " trước nhé.";
            } else {
                message = "Hãy tiếp tục cố gắng ở mức A1 nhé!";
            }
        }

        // Đóng gói trả về
        TestSubmissionResponseDto response = new TestSubmissionResponseDto();
        response.setTotalQuestions(totalQuestions);
        response.setCorrectAnswers(correctCount);
        response.setScorePercentage(scorePercentage);
        response.setPassedThreshold(passed);
        response.setTestedLevel(request.getTestedLevel());
        response.setRecommendedLevel(recommended);
        response.setSystemMessage(message);
        response.setReviewList(reviewList);

        return response;
    }

    // 3. API Chốt Level của User
    public void setUserCurrentLevel(SetLevelRequestDto request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy User"));

        user.setCurrentLevel(request.getSelectedLevel());
        userRepository.save(user);
    }

}
