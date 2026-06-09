package com.longdq.adaptengbackend.service;

import com.longdq.adaptengbackend.dto.DailyReviewResultResponseDto;
import com.longdq.adaptengbackend.dto.DailyReviewSubmissionRequestDto;
import com.longdq.adaptengbackend.dto.QuestionResponseDto;
import com.longdq.adaptengbackend.entity.Question;
import com.longdq.adaptengbackend.entity.User;
import com.longdq.adaptengbackend.entity.UserLearningProgress;
import com.longdq.adaptengbackend.entity.UserQuestionHistory;
import com.longdq.adaptengbackend.repository.QuestionRepository;
import com.longdq.adaptengbackend.repository.UserLearningProgressRepository;
import com.longdq.adaptengbackend.repository.UserQuestionHistoryRepository;
import com.longdq.adaptengbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PracticeService {

    private final UserLearningProgressRepository progressRepository;
    private final QuestionRepository questionRepository;
    private final UserQuestionHistoryRepository userQuestionHistoryRepository;
    private final SpacedRepetitionService spacedRepetitionService;

    public List<QuestionResponseDto> generateDailyReviewTest() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        List<Question> finalTestQuestions = new ArrayList<>();
        List<Long> pickedQuestionIds = new ArrayList<>();
        pickedQuestionIds.add(-1L); // Giá trị ảo để tránh lỗi cú pháp "NOT IN ()" trong SQL

        int MAX_QUESTIONS = 30;

        // 1. Lấy danh sách cần ôn tập (Đã ưu tiên câu vừa sai lên đầu)
        List<UserLearningProgress> dueItems = progressRepository
                .findByUserIdAndNextReviewDateLessThanEqualOrderByIntervalDaysAscNextReviewDateAsc(
                        user.getId(), LocalDateTime.now()
                );

        // 2. Vòng lặp bốc câu hỏi theo thuật toán SM-2 kèm Fallback vòng lặp LRU
        for (UserLearningProgress item : dueItems) {
            if (finalTestQuestions.size() >= MAX_QUESTIONS) break;

            UUID knowledgeId = item.getKnowledgeItem() != null ? item.getKnowledgeItem().getId() : null;
            String targetWord = item.getTargetWord();

            // Thử bốc câu hỏi mới toanh (chưa từng làm)
            Optional<Question> questionOpt = questionRepository.findRandomQuestionForReview(
                    knowledgeId, targetWord, user.getId(), pickedQuestionIds
            );

            // FALLBACK LOGIC: Nếu hết câu mới -> Bốc lại câu đã làm từ lâu nhất (LRU)
            if (questionOpt.isEmpty()) {
                questionOpt = questionRepository.findOldestAnsweredQuestionForReview(
                        knowledgeId, targetWord, user.getId(), pickedQuestionIds
                );
            }

            if (questionOpt.isPresent()) {
                Question q = questionOpt.get();
                finalTestQuestions.add(q);
                pickedQuestionIds.add(q.getId());
            }
        }

        // 3. Lấp chỗ trống nếu chưa đủ 30 câu (Khám phá lỗ hổng mới)
        int missingCount = MAX_QUESTIONS - finalTestQuestions.size();
        if (missingCount > 0) {
            List<Question> fillQuestions = questionRepository.findRandomQuestionsToFill(
                    user.getCurrentLevel().name(), user.getId(), pickedQuestionIds, missingCount
            );
            finalTestQuestions.addAll(fillQuestions);
        }

        // 4. Xáo trộn ngẫu nhiên đề thi
        Collections.shuffle(finalTestQuestions);

        // 5. Mapping dữ liệu trả về cho Frontend
        return finalTestQuestions.stream().map(q -> {
            QuestionResponseDto dto = new QuestionResponseDto();
            dto.setQuestionId(q.getId());
            dto.setContent(q.getContent());
            dto.setOptions(q.getOptions());
            dto.setQuestionType(q.getQuestionType());
            return dto;
        }).collect(Collectors.toList());
    }

    @Transactional // RẤT QUAN TRỌNG
    public DailyReviewResultResponseDto submitDailyReview(DailyReviewSubmissionRequestDto request) {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        LocalDateTime now = LocalDateTime.now();

        // 1. TỐI ƯU SELECT: Lấy TẤT CẢ 30 câu hỏi bằng 1 query
        List<Long> questionIds = request.getAnswers().stream()
                .map(DailyReviewSubmissionRequestDto.UserAnswerDto::getQuestionId)
                .collect(Collectors.toList());

        Map<Long, Question> questionMap = questionRepository.findAllById(questionIds).stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        // 2. KHỞI TẠO "THÙNG CHỨA"
        List<UserQuestionHistory> historiesToSave = new ArrayList<>();
        List<DailyReviewResultResponseDto.QuestionReviewDto> results = new ArrayList<>();

        // CHÌA KHÓA VÀNG Ở ĐÂY: Dùng Map làm RAM Cache thay vì List để chống trùng lặp Progress
        Map<String, UserLearningProgress> progressCache = new HashMap<>();

        int correctCount = 0;

        // 3. XỬ LÝ LOGIC TRONG RAM
        for (DailyReviewSubmissionRequestDto.UserAnswerDto answer : request.getAnswers()) {
            Question question = questionMap.get(answer.getQuestionId());
            if (question == null) continue;

            boolean isCorrect = question.getCorrectAnswer().equalsIgnoreCase(answer.getSelectedAnswer());
            if (isCorrect) correctCount++;

            // Lịch sử thì không sợ trùng, cứ tống vào List
            UserQuestionHistory history = new UserQuestionHistory();
            history.setUserId(user.getId());
            history.setQuestionId(question.getId());
            history.setCorrect(isCorrect);
            history.setAnsweredAt(now);
            historiesToSave.add(history);

            UUID knowledgeId = question.getKnowledgeItem() != null ? question.getKnowledgeItem().getId() : null;
            String targetWord = question.getTargetWord();

            // TẠO KEY DUY NHẤT CHO MAP CACHE
            String cacheKey = (knowledgeId != null ? knowledgeId.toString() : "null") + "_" +
                    (targetWord != null ? targetWord : "null");

            // Ưu tiên lấy Progress từ Cache RAM. Nếu chưa có mới chọc xuống DB.
            UserLearningProgress progress = progressCache.get(cacheKey);

            if (progress == null) {
                progress = progressRepository.findProgressRecord(user.getId(), knowledgeId, targetWord)
                        .orElseGet(() -> {
                            UserLearningProgress newProgress = new UserLearningProgress();
                            newProgress.setUser(user);
                            newProgress.setKnowledgeItem(question.getKnowledgeItem());
                            newProgress.setTargetWord(targetWord);
                            newProgress.setEaseFactor(2.5);
                            newProgress.setRepetitionCount(0);
                            newProgress.setIntervalDays(1);
                            return newProgress;
                        });
                // Lấy từ DB lên xong thì nhét ngay vào Cache
                progressCache.put(cacheKey, progress);
            }

            // Tính toán SM-2 trên RAM (Object này đã được tham chiếu thẳng vào trong Map)
            spacedRepetitionService.updateProgress(progress, isCorrect);

            // Đóng gói DTO gửi về Frontend
            DailyReviewResultResponseDto.QuestionReviewDto reviewDto = new DailyReviewResultResponseDto.QuestionReviewDto();
            reviewDto.setQuestionId(question.getId());
            reviewDto.setUserSelectedAnswer(answer.getSelectedAnswer());
            reviewDto.setCorrectAnswer(question.getCorrectAnswer());
            reviewDto.setCorrect(isCorrect);
            reviewDto.setExplanation(question.getExplanation());

            String kName = "";
            if (targetWord != null) {
                kName = "Từ vựng: " + targetWord;
            } else if (question.getKnowledgeItem() != null) {
                kName = question.getKnowledgeItem().getKnowledgeName();
            }
            reviewDto.setKnowledgeName(kName);

            results.add(reviewDto);
        }

        // 4. BATCH INSERT/UPDATE: Lệnh gọi DB duy nhất để lưu TẤT CẢ
        userQuestionHistoryRepository.saveAll(historiesToSave);

        // Chỉ lưu những value đã được chắt lọc duy nhất (Distinct) trong Cache
        progressRepository.saveAll(progressCache.values());

        return new DailyReviewResultResponseDto(request.getAnswers().size(), correctCount, results);
    }
}