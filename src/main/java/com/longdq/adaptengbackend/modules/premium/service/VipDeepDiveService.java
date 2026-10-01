package com.longdq.adaptengbackend.modules.premium.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.longdq.adaptengbackend.modules.premium.dto.DeepDiveDto;
import com.longdq.adaptengbackend.modules.progress.entity.AppConfig;
import com.longdq.adaptengbackend.modules.premium.entity.DeepDiveSession;
import com.longdq.adaptengbackend.modules.premium.entity.DeepDiveQuestion;
import com.longdq.adaptengbackend.modules.premium.entity.UserDeepDiveHistory;
import com.longdq.adaptengbackend.modules.user.entity.User;
import com.longdq.adaptengbackend.modules.spacedrepetition.entity.UserLearningProgress;
import com.longdq.adaptengbackend.common.enums.DeepDiveStatus;
import com.longdq.adaptengbackend.common.enums.Level;
import com.longdq.adaptengbackend.common.exception.QuotaExceededException;
import com.longdq.adaptengbackend.common.exception.ResourceNotFoundException;
import com.longdq.adaptengbackend.common.exception.ValidationException;
import com.longdq.adaptengbackend.modules.progress.repository.AppConfigRepository;
import com.longdq.adaptengbackend.modules.premium.repository.DeepDiveSessionRepository;
import com.longdq.adaptengbackend.modules.premium.repository.DeepDiveQuestionRepository;
import com.longdq.adaptengbackend.modules.premium.repository.UserDeepDiveHistoryRepository;
import com.longdq.adaptengbackend.modules.spacedrepetition.repository.UserLearningProgressRepository;
import com.longdq.adaptengbackend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VipDeepDiveService {

    private final UserLearningProgressRepository progressRepository;
    private final DeepDiveSessionRepository sessionRepository;
    private final AppConfigRepository appConfigRepository;
    private final DeepDiveAiWorker aiWorker;
    private final UserRepository userRepository;
    private final DeepDiveQuestionRepository deepDiveQuestionRepository;
    private final UserDeepDiveHistoryRepository historyRepository;
    private final ObjectMapper objectMapper;

    // ==========================================
    // 1. LẤY TOP 10 ĐIỂM YẾU
    // ==========================================
    public List<DeepDiveDto.RecommendationResponse> getTopWeaknesses(UUID userId) {
        LocalDateTime threshold = LocalDateTime.now().minusHours(48);

        List<UserLearningProgress> weaknesses = progressRepository.findTopWeaknessesForDeepDive(
                userId, threshold, PageRequest.of(0, 10));

        return weaknesses.stream().map(w -> {
            UUID kId = w.getKnowledgeItem() != null ? w.getKnowledgeItem().getId() : null;
            String kName = w.getKnowledgeItem() != null ? w.getKnowledgeItem().getKnowledgeName() : "Chủ điểm";

            Optional<DeepDiveSession> activeSession = sessionRepository.findFirstByUserIdAndKnowledgeItemIdAndTargetWordAndStatusInOrderByCreatedAtDesc(
                    userId, kId, w.getTargetWord(),
                    List.of(DeepDiveStatus.GENERATING, DeepDiveStatus.READY)
            );

            return DeepDiveDto.RecommendationResponse.builder()
                    .knowledgeItemId(kId)
                    .targetWord(w.getTargetWord())
                    .knowledgeName(kName)
                    .easeFactor(w.getEaseFactor())
                    .difficultyLevel(w.getEaseFactor() < 1.8 ? "Rất cao" : "Cao")
                    .activeSessionId(activeSession.map(DeepDiveSession::getId).orElse(null))
                    .activeSessionStatus(activeSession.map(s -> s.getStatus().name()).orElse(null))
                    .build();
        }).collect(Collectors.toList());
    }

    // ==========================================
    // 2. TẠO SESSION & GỌI AI NGẦM
    // ==========================================
    @Transactional
    public DeepDiveDto.InitResponse initSession(UUID userId, UUID knowledgeItemId, String targetWord) {

        Optional<DeepDiveSession> existingSession = sessionRepository.findFirstByUserIdAndKnowledgeItemIdAndTargetWordAndStatusInOrderByCreatedAtDesc(
                userId, knowledgeItemId, targetWord,
                List.of(DeepDiveStatus.GENERATING, DeepDiveStatus.READY)
        );

        if (existingSession.isPresent()) {
            DeepDiveSession activeSession = existingSession.get();
            return DeepDiveDto.InitResponse.builder()
                    .sessionId(activeSession.getId())
                    .status(activeSession.getStatus().name())
                    .message("EXISTING_SESSION_RETRIEVED")
                    .build();
        }

        int dailyQuota = Integer.parseInt(getConfig("VIP_DEEP_DIVE_DAILY_QUOTA", "3"));
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        long todayCount = sessionRepository.countByUserIdAndCreatedAtGreaterThanEqual(userId, startOfDay);

        if (todayCount >= dailyQuota) {
            throw new QuotaExceededException("MAX_LIMIT_REACHED");
        }

        DeepDiveSession session = DeepDiveSession.builder()
                .userId(userId)
                .knowledgeItemId(knowledgeItemId)
                .targetWord(targetWord)
                .status(DeepDiveStatus.GENERATING)
                .build();
        sessionRepository.save(session);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND"));
        Level targetLevel = user.getCurrentLevel() != null ? user.getCurrentLevel() : Level.B1;

        aiWorker.generateQuestionsAsync(session.getId(), userId, knowledgeItemId, targetWord, targetLevel);

        return DeepDiveDto.InitResponse.builder()
                .sessionId(session.getId())
                .status(DeepDiveStatus.GENERATING.name())
                .message("NEW_SESSION_CREATED")
                .build();
    }

    // ==========================================
    // 3. LẤY DANH SÁCH CÂU HỎI TỪ BỘ ĐỀ VIP
    // ==========================================
    public List<DeepDiveDto.PassageResponse> getSessionQuestions(UUID sessionId) {
        DeepDiveSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("SESSION_NOT_FOUND"));

        if (session.getStatus() != DeepDiveStatus.READY && session.getStatus() != DeepDiveStatus.COMPLETED) {
            throw new ResourceNotFoundException("Đề chưa sẵn sàng hoặc đang tạo!");
        }

        try {
            List<Long> questionIds = objectMapper.readValue(session.getQuestionsJson(), new TypeReference<List<Long>>() {});
            List<DeepDiveQuestion> questions = deepDiveQuestionRepository.findAllById(questionIds);

            List<DeepDiveDto.PassageResponse> passageResponses = new ArrayList<>();
            List<DeepDiveDto.QuestionResponse> standaloneQuestions = new ArrayList<>();

            Map<String, List<DeepDiveQuestion>> passageGroups = new LinkedHashMap<>();

            for (DeepDiveQuestion q : questions) {
                DeepDiveDto.QuestionResponse qDto = DeepDiveDto.QuestionResponse.builder()
                        .questionId(q.getId())
                        .content(q.getContent())
                        .options(q.getOptions())
                        .build();

                if (q.getPassageContent() == null || q.getPassageContent().trim().isEmpty()) {
                    standaloneQuestions.add(qDto);
                } else {
                    passageGroups.computeIfAbsent(q.getPassageContent(), k -> new ArrayList<>()).add(q);
                }
            }

            if (!standaloneQuestions.isEmpty()) {
                passageResponses.add(DeepDiveDto.PassageResponse.builder()
                        .passageId(0L)
                        .toeicPart("PART_5")
                        .passageContent(null)
                        .questions(standaloneQuestions)
                        .build());
            }

            long fakePassageId = 1L;
            for (Map.Entry<String, List<DeepDiveQuestion>> entry : passageGroups.entrySet()) {
                List<DeepDiveDto.QuestionResponse> groupQuestions = entry.getValue().stream()
                        .map(q -> DeepDiveDto.QuestionResponse.builder()
                                .questionId(q.getId())
                                .content(q.getContent())
                                .options(q.getOptions())
                                .build())
                        .collect(Collectors.toList());

                passageResponses.add(DeepDiveDto.PassageResponse.builder()
                        .passageId(fakePassageId++)
                        .toeicPart("PART_7_SINGLE")
                        .passageContent(entry.getKey())
                        .questions(groupQuestions)
                        .build());
            }

            return passageResponses;
        } catch (Exception e) {
            throw new RuntimeException("Lỗi xử lý và nhóm dữ liệu câu hỏi", e);
        }
    }

    // ==========================================
    // 4. CHẤM ĐIỂM + LƯU LỊCH SỬ TÁI SỬ DỤNG + SM-2
    // ==========================================
    @Transactional
    public DeepDiveDto.SubmitResponse gradeAndSubmitSession(UUID userId, UUID sessionId, DeepDiveDto.SubmitRequest request) {
        DeepDiveSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("SESSION_NOT_FOUND"));

        if (session.getStatus() == DeepDiveStatus.COMPLETED) {
            throw new ValidationException("Phiên học này đã được nộp trước đó.");
        }

        try {
            List<Long> questionIds = objectMapper.readValue(session.getQuestionsJson(), new TypeReference<List<Long>>() {});
            List<DeepDiveQuestion> questions = deepDiveQuestionRepository.findAllById(questionIds);

            int correctCount = 0;
            List<DeepDiveDto.ReviewDto> reviewList = new ArrayList<>();

            for (DeepDiveQuestion q : questions) {
                String userAnswer = request.getAnswers().getOrDefault(q.getId(), "");
                boolean isCorrect = q.getCorrectAnswer().equalsIgnoreCase(userAnswer);

                if (isCorrect) {
                    correctCount++;
                }

                reviewList.add(DeepDiveDto.ReviewDto.builder()
                        .questionId(q.getId())
                        .isCorrect(isCorrect)
                        .userSelectedAnswer(userAnswer)
                        .correctAnswer(q.getCorrectAnswer())
                        .explanation(q.getExplanation())
                        .knowledgeName(q.getKnowledgeName())
                        .build());
            }
            double scorePercent = (correctCount * 100.0) / questions.size();

            session.setUserAnswersJson(objectMapper.writeValueAsString(request.getAnswers()));

            if (!questions.isEmpty()) {
                UUID setId = questions.get(0).getDeepDiveSet().getId();
                UserDeepDiveHistory history = UserDeepDiveHistory.builder()
                        .userId(userId)
                        .deepDiveSetId(setId)
                        .scorePercent(scorePercent)
                        .build();
                historyRepository.save(history);
            }

            double excThreshold = Double.parseDouble(getConfig("DEEP_DIVE_EXCELLENT_THRESHOLD", "80"));
            double goodThreshold = Double.parseDouble(getConfig("DEEP_DIVE_GOOD_THRESHOLD", "50"));
            double excBonus = Double.parseDouble(getConfig("DEEP_DIVE_EXCELLENT_EF_BONUS", "0.3"));
            double goodBonus = Double.parseDouble(getConfig("DEEP_DIVE_GOOD_EF_BONUS", "0.2"));
            double poorBonus = Double.parseDouble(getConfig("DEEP_DIVE_POOR_EF_BONUS", "0.1"));
            int poorInterval = Integer.parseInt(getConfig("DEEP_DIVE_POOR_INTERVAL", "1"));

            UserLearningProgress progress = progressRepository.findProgressRecord(
                    userId, session.getKnowledgeItemId(), session.getTargetWord()
            ).orElseThrow(() -> new RuntimeException("Không tìm thấy tiến trình học"));

            if (scorePercent >= excThreshold) {
                // Làm xuất sắc: Tăng Ease Factor và nhân Interval để giãn khoảng cách ôn tập ra rất xa
                progress.setEaseFactor(progress.getEaseFactor() + excBonus);
                int newInterval = Math.max(1, (int) Math.round(progress.getIntervalDays() * progress.getEaseFactor()));
                progress.setIntervalDays(newInterval);
            } else if (scorePercent >= goodThreshold) {
                // Làm khá: Tăng nhẹ Ease Factor và giãn khoảng cách ôn tập vừa phải
                progress.setEaseFactor(progress.getEaseFactor() + goodBonus);
                int newInterval = Math.max(1, (int) Math.round(progress.getIntervalDays() * progress.getEaseFactor()));
                progress.setIntervalDays(newInterval);
            } else {
                // Làm tệ (như case 30% vừa rồi): Giảm nhẹ Ease Factor và ép ôn tập lại vào NGÀY MAI
                // (SM-2 chuẩn thì điểm thấp phải bị trừ easeFactor, chứ không cộng như cũ)
                double newEase = Math.max(1.3, progress.getEaseFactor() - poorBonus);
                progress.setEaseFactor(newEase);
                progress.setIntervalDays(poorInterval); // Ép về 1 ngày
            }

            progress.setLastReviewDate(LocalDateTime.now());
            progress.setNextReviewDate(LocalDateTime.now().plusDays(progress.getIntervalDays()));
            progressRepository.save(progress);

            session.setStatus(DeepDiveStatus.COMPLETED);
            session.setScore((int) (scorePercent / 10));
            sessionRepository.save(session);

            // 🚀 ĐÃ SỬA: Cập nhật XP chuẩn theo kiểu nguyên thủy (int)
            int earnedXp = correctCount * 5;
            if (earnedXp > 0) {
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND"));
                user.setTotalXp(user.getTotalXp() + earnedXp);
                userRepository.save(user);
            }

            return DeepDiveDto.SubmitResponse.builder()
                    .score(correctCount)
                    .total(questions.size())
                    .scorePercent(scorePercent)
                    .reviewList(reviewList)
                    .message("Nộp bài thành công!")
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Lỗi xử lý nộp bài: " + e.getMessage(), e);
        }
    }

    private String getConfig(String key, String defaultValue) {
        return appConfigRepository.findById(key).map(AppConfig::getConfigValue).orElse(defaultValue);
    }
}