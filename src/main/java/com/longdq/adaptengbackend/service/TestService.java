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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class TestService {
    private final QuestionRepository questionRepository;
    private final UserLearningProgressRepository userLearningProgressRepository;
    private final UserRepository userRepository;

    public List<QuestionResponseDto> getTestQuestions(Level level){
        List<Question> questionList = questionRepository.find30RandomTestQuestionsByLevel(level);
        return questionList.stream().map(q -> new QuestionResponseDto(q.getId(), q.getContent(),
                q.getOptions(), q.getQuestionType())).toList();
    }

    @Transactional // THÊM CÁI NÀY ĐỂ BẢO VỆ DATABASE NẾU LỖI GIỮA CHỪNG
    public TestSubmissionResponseDto submitTest(TestSubmissionRequestDto request) {
        int totalQuestions = request.getAnswers().size();
        int correctCount = 0;
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        List<TestSubmissionResponseDto.QuestionReviewDto> reviewList = new ArrayList<>();

        List<Question> questions = questionRepository.findAllById(request.getAnswers().stream().map(TestSubmissionRequestDto.UserAnswerDto::getQuestionId).toList());
        Map<Long, Question> questionMap = questions.stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));

        // TỪ BỎ "Set<KnowledgeItem>". DÙNG MAP CACHE ĐỂ LƯU NỢ CHUẨN XÁC ĐẾN TỪNG CHỮ
        Map<String, UserLearningProgress> progressCache = new HashMap<>();

        for (TestSubmissionRequestDto.UserAnswerDto answer : request.getAnswers()) {
            Question question = questionMap.get(answer.getQuestionId());
            if (question == null) continue;

            boolean isCorrect = question.getCorrectAnswer().equalsIgnoreCase(answer.getSelectedAnswer());

            if (isCorrect) {
                correctCount++;
            } else {
                // XỬ LÝ LƯU NỢ VÀO BẢNG PROGRESS (NẾU SAI)
                UUID knowledgeId = question.getKnowledgeItem() != null ? question.getKnowledgeItem().getId() : null;
                String targetWord = question.getTargetWord();

                // Tạo bộ khóa nhận diện: Ví dụ "uuid-123_apple"
                if (knowledgeId != null || targetWord != null) {
                    String cacheKey = (knowledgeId != null ? knowledgeId.toString() : "null") + "_" +
                            (targetWord != null ? targetWord : "null");

                    // Nếu chưa có trong túi thì lấy từ DB lên hoặc tạo mới
                    if (!progressCache.containsKey(cacheKey)) {
                        UserLearningProgress progress = userLearningProgressRepository
                                .findProgressRecord(user.getId(), knowledgeId, targetWord)
                                .orElseGet(() -> {
                                    UserLearningProgress newProgress = new UserLearningProgress();
                                    newProgress.setUser(user);
                                    newProgress.setKnowledgeItem(question.getKnowledgeItem());
                                    // 👇 ĐÂY LÀ DÒNG CODE VÁ LỖI CỦA BẠN: HỨNG TỪ VỰNG TỪ QUESTION
                                    newProgress.setTargetWord(targetWord);
                                    newProgress.setIntervalDays(0);
                                    newProgress.setRepetitionCount(0);
                                    newProgress.setEaseFactor(1.3);
                                    newProgress.setNextReviewDate(LocalDateTime.now().plusDays(1));
                                    return newProgress;
                                });
                        // Bỏ vào Cache
                        progressCache.put(cacheKey, progress);
                    }
                }
            }

            // Đổ data vào Review Dto trả về UI
            TestSubmissionResponseDto.QuestionReviewDto review = new TestSubmissionResponseDto.QuestionReviewDto();
            review.setQuestionId(question.getId());
            review.setUserSelectedAnswer(answer.getSelectedAnswer());
            review.setCorrectAnswer(question.getCorrectAnswer());
            review.setCorrect(isCorrect);
            review.setExplanation(question.getExplanation());

            // Xử lý hiển thị tên kiến thức cho UI đẹp hơn
            String kName = "";
            if (question.getTargetWord() != null) {
                kName = "Từ vựng: " + question.getTargetWord();
            } else if (question.getKnowledgeItem() != null) {
                kName = question.getKnowledgeItem().getKnowledgeName();
            }
            review.setKnowledgeName(kName);

            reviewList.add(review);
        }

        // BATCH SAVE: LƯU HÀNG LOẠT TIẾN ĐỘ CHỈ BẰNG 1 CÂU QUERY DUY NHẤT 🚀
        userLearningProgressRepository.saveAll(progressCache.values());

        // THUẬT TOÁN HẠ CẤP (DOWNGRADE LEVEL) - Giữ nguyên của bạn
        double scorePercentage = (double) correctCount / totalQuestions * 100;
        boolean passed = scorePercentage >= 70.0;

        Level recommended = request.getTestedLevel();
        String message = "Tuyệt vời! Trình độ của bạn hoàn toàn phù hợp với mức " + recommended + ".";

        if (!passed) {
            int currentOrdinal = request.getTestedLevel().ordinal();
            if (currentOrdinal > 0) {
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

    public void setUserCurrentLevel(SetLevelRequestDto request) {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        user.setCurrentLevel(request.getSelectedLevel());
        userRepository.save(user);
    }
}