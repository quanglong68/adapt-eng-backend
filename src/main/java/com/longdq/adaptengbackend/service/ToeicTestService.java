package com.longdq.adaptengbackend.service;

import com.longdq.adaptengbackend.dto.*;
import com.longdq.adaptengbackend.entity.Passage;
import com.longdq.adaptengbackend.entity.Question;
import com.longdq.adaptengbackend.entity.User;
import com.longdq.adaptengbackend.entity.UserLearningProgress;
import com.longdq.adaptengbackend.enums.Level;
import com.longdq.adaptengbackend.enums.ToeicPart;
import com.longdq.adaptengbackend.repository.PassageRepository;
import com.longdq.adaptengbackend.repository.QuestionRepository;
import com.longdq.adaptengbackend.repository.UserLearningProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ToeicTestService {

    private final QuestionRepository questionRepository;
    private final PassageRepository passageRepository;
    private final UserLearningProgressRepository progressRepository;

    public List<ToeicPassageResponseDto> getToeicTestQuestions(Level level) {
        List<ToeicPassageResponseDto> finalResponseBlocks = new ArrayList<>();

        // 1. Xử lý Part 5: Gom toàn bộ 15 câu hỏi đơn lẻ vào 1 Block duy nhất (passageContent = null)
        List<Question> part5Questions = questionRepository.findRandomToeicQuestions(ToeicPart.PART_5.name(), level.name(), 15);
        if (!part5Questions.isEmpty()) {
            List<ToeicQuestionResponseDto> p5QuestionDtos = part5Questions.stream().map(q ->
                    new ToeicQuestionResponseDto(q.getId(), q.getContent(), q.getOptions(), q.getQuestionType())
            ).collect(Collectors.toList());

            finalResponseBlocks.add(new ToeicPassageResponseDto(null, null, ToeicPart.PART_5, p5QuestionDtos));
        }

        // 2. Xử lý các Part có bài đọc: Trích xuất trọn gói theo khối phân tầng
        fetchAndGroupToeicPassages(ToeicPart.PART_6, level, 2, finalResponseBlocks);
        fetchAndGroupToeicPassages(ToeicPart.PART_7_SINGLE, level, 3, finalResponseBlocks);
        fetchAndGroupToeicPassages(ToeicPart.PART_7_MULTIPLE, level, 3, finalResponseBlocks);

        return finalResponseBlocks;
    }

    private void fetchAndGroupToeicPassages(ToeicPart part, Level level, int limit, List<ToeicPassageResponseDto> responseBlocks) {
        List<Passage> passages = passageRepository.findRandomToeicPassages(part.name(), level.name(), limit);
        for (Passage p : passages) {
            List<ToeicQuestionResponseDto> questionDtos = p.getQuestions().stream().map(q ->
                    new ToeicQuestionResponseDto(q.getId(), q.getContent(), q.getOptions(), q.getQuestionType())
            ).collect(Collectors.toList());

            // Nội dung đoạn văn chỉ xuất hiện duy nhất 1 lần trong Block này
            responseBlocks.add(new ToeicPassageResponseDto(p.getId(), p.getContent(), part, questionDtos));
        }
    }

    @Transactional
    public TestSubmissionResponseDto submitToeicTest(TestSubmissionRequestDto request) {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        List<TestSubmissionResponseDto.QuestionReviewDto> reviewList = new ArrayList<>();
        Map<String, UserLearningProgress> progressCache = new HashMap<>();

        int totalQuestions = request.getAnswers().size();
        int correctCount = 0;

        List<Question> questions = questionRepository.findAllById(request.getAnswers().stream().map(TestSubmissionRequestDto.UserAnswerDto::getQuestionId).toList());
        Map<Long, Question> questionMap = questions.stream().collect(Collectors.toMap(Question::getId, Function.identity()));

        for (TestSubmissionRequestDto.UserAnswerDto answer : request.getAnswers()) {
            Question question = questionMap.get(answer.getQuestionId());
            if (question == null) continue;

            boolean isCorrect = question.getCorrectAnswer().equalsIgnoreCase(answer.getSelectedAnswer());

            if (isCorrect) {
                correctCount++;
            } else {
                // XỬ LÝ LƯU NỢ VÀO BẢNG PROGRESS CHO TOEIC
                UUID knowledgeId = question.getKnowledgeItem() != null ? question.getKnowledgeItem().getId() : null;
                String targetWord = question.getTargetWord();

                if (knowledgeId != null || targetWord != null) {
                    String cacheKey = (knowledgeId != null ? knowledgeId.toString() : "null") + "_" + (targetWord != null ? targetWord : "null");
                    if (!progressCache.containsKey(cacheKey)) {
                        UserLearningProgress progress = progressRepository.findProgressRecord(user.getId(), knowledgeId, targetWord)
                                .orElseGet(() -> {
                                    UserLearningProgress newProgress = new UserLearningProgress();
                                    newProgress.setUser(user);
                                    newProgress.setKnowledgeItem(question.getKnowledgeItem());
                                    newProgress.setTargetWord(targetWord);
                                    newProgress.setIntervalDays(0);
                                    newProgress.setRepetitionCount(0);
                                    newProgress.setEaseFactor(1.3);
                                    newProgress.setNextReviewDate(LocalDateTime.now().plusDays(1));
                                    newProgress.setToeicPart(question.getToeicPart());
                                    return newProgress;
                                });
                        progressCache.put(cacheKey, progress);
                    }
                }
            }

            TestSubmissionResponseDto.QuestionReviewDto review = new TestSubmissionResponseDto.QuestionReviewDto();
            review.setQuestionId(question.getId());
            review.setUserSelectedAnswer(answer.getSelectedAnswer());
            review.setCorrectAnswer(question.getCorrectAnswer());
            review.setCorrect(isCorrect);
            review.setExplanation(question.getExplanation());

            // 👇 CHUẨN HÓA LẠI TÊN KIẾN THỨC THEO ĐÚNG CODE CŨ CỦA BẠN
            String kName = "";
            if (question.getKnowledgeItem() != null) {
                // Ưu tiên lấy tên chủ điểm tiếng Việt (VD: "Thì hiện tại đơn")
                kName = question.getKnowledgeItem().getKnowledgeName();
                // Nếu câu này có test cụ thể một từ, thì nối thêm vào cho rõ ràng
                if (question.getTargetWord() != null) {
                    kName += " (Từ khóa: " + question.getTargetWord() + ")";
                }
            } else if (question.getTargetWord() != null) {
                kName = "Từ vựng: " + question.getTargetWord(); // Dự phòng
            }
            review.setKnowledgeName(kName);

            reviewList.add(review);
        }

        progressRepository.saveAll(progressCache.values());

        // 👇 BÊ NGUYÊN XI THUẬT TOÁN HẠ CẤP TRÌNH ĐỘ TỪ CODE CŨ SANG
        double scorePercentage = totalQuestions == 0 ? 0 : (double) correctCount / totalQuestions * 100;
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

        // 👇 ĐÓNG GÓI TRẢ VỀ ĐẦY ĐỦ CÁC TRƯỜNG DTO
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
}