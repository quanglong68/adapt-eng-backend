package com.longdq.adaptengbackend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.longdq.adaptengbackend.dto.*;
import com.longdq.adaptengbackend.entity.*;
import com.longdq.adaptengbackend.enums.Purpose;
import com.longdq.adaptengbackend.enums.TestRecordStatus;
import com.longdq.adaptengbackend.enums.ToeicPart;
import com.longdq.adaptengbackend.enums.Level;
import com.longdq.adaptengbackend.exception.BusinessException;
import com.longdq.adaptengbackend.repository.*;
import com.longdq.adaptengbackend.util.DailyReviewProgressHelper;
import com.longdq.adaptengbackend.util.KnowledgeDisplayNameUtil;
import com.longdq.adaptengbackend.util.PremiumCheckUtil; // Đã import Util xịn của bác
import com.longdq.adaptengbackend.util.ProgressCacheKeyUtil;
import com.longdq.adaptengbackend.util.QuestionReviewBuilder;
import com.longdq.adaptengbackend.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ToeicPracticeService {

    private final UserLearningProgressRepository progressRepository;
    private final QuestionRepository questionRepository;
    private final PassageRepository passageRepository;
    private final UserQuestionHistoryRepository userQuestionHistoryRepository;
    private final SpacedRepetitionService spacedRepetitionService;
    private final UserRepository userRepository;
    private final DataSyncService dataSyncService;
    private final DailyTestRecordRepository recordRepository;
    private final ObjectMapper objectMapper;
    private final AppConfigRepository appConfigRepository;

    // TIÊM UTIL XỊN VÀO ĐÂY
    private final PremiumCheckUtil premiumCheckUtil;

    // =========================================================================
    // 1. QUẢN LÝ LUỒNG LÀM BÀI HÀNG NGÀY (FREE/VIP QUOTA & AUTO-SAVE)
    // =========================================================================

    @Transactional
    public DailyPracticeSessionDto getOrCreateDailyPractice() {
        User user = SecurityUtils.getCurrentUser();
        LocalDate today = LocalDate.now();

        // A. DỌN DẸP LỖ HỔNG THỜI GIAN: Hủy bỏ các đề làm dở từ hôm qua trở về trước
        List<DailyTestRecord> oldPendingRecords = recordRepository
                .findByUserIdAndStatusAndTestDateLessThan(user.getId(), TestRecordStatus.IN_PROGRESS, today);
        for (DailyTestRecord oldRecord : oldPendingRecords) {
            oldRecord.setStatus(TestRecordStatus.EXPIRED);
            oldRecord.setUpdatedAt(LocalDateTime.now());
        }
        if (!oldPendingRecords.isEmpty()) {
            recordRepository.saveAll(oldPendingRecords);
        }

        // B. TÌM ĐỀ ĐANG LÀM DỞ CỦA HÔM NAY
        Optional<DailyTestRecord> pendingRecordOpt = recordRepository
                .findFirstByUserIdAndTestDateAndStatus(user.getId(), today, TestRecordStatus.IN_PROGRESS);

        DailyTestRecord todayRecord;
        List<ToeicPassageResponseDto> testContent;
        Map<Long, String> savedAnswers = new HashMap<>();

        if (pendingRecordOpt.isPresent()) {
            // NẾU CÓ ĐỀ ĐANG LÀM DỞ -> Trả về y nguyên để user làm tiếp
            todayRecord = pendingRecordOpt.get();
            try {
                testContent = objectMapper.readValue(todayRecord.getQuestionsJson(), new TypeReference<List<ToeicPassageResponseDto>>() {});
                if (todayRecord.getUserAnswersJson() != null) {
                    savedAnswers = objectMapper.readValue(todayRecord.getUserAnswersJson(), new TypeReference<Map<Long, String>>() {});
                }
            } catch (JsonProcessingException e) {
                log.error("Lỗi parse JSON đề bài", e);
                throw new RuntimeException("Cannot parse test data");
            }
        } else {
            // =======================================================
            // C. CHƯA CÓ ĐỀ DỞ DANG -> KIỂM TRA QUOTA ĐỂ TẠO ĐỀ MỚI
            // =======================================================
            long completedCount = recordRepository.countByUserIdAndTestDateAndStatus(user.getId(), today, TestRecordStatus.COMPLETED);

            if (completedCount >= 1) {
                // SỬ DỤNG HÀM TÁI SỬ DỤNG CHUẨN XÁC
                boolean isVip = premiumCheckUtil.isPremiumUser(user.getId());

                if (!isVip) {
                    // Ném lỗi BusinessException để FE bắt và hiện UI mua VIP
                    throw new IllegalArgumentException("REQUIRE_VIP");
                }

                if (completedCount >= 3) {
                    // VIP cũng chỉ được làm 3 đề/ngày
                    throw new IllegalArgumentException("MAX_LIMIT_REACHED");
                }
            }

            // ĐỦ ĐIỀU KIỆN QUOTA -> Kích hoạt SM-2 bốc đề mới toanh
            testContent = buildNewTestBlocks(user);

            todayRecord = new DailyTestRecord();
            todayRecord.setUserId(user.getId());
            todayRecord.setTestDate(today);
            todayRecord.setStatus(TestRecordStatus.IN_PROGRESS);
            todayRecord.setLevel(user.getCurrentLevel());

            int totalQ = testContent.stream()
                    .mapToInt(block -> block.getQuestions().size())
                    .sum();
            todayRecord.setTotalQuestions(totalQ);

            try {
                todayRecord.setQuestionsJson(objectMapper.writeValueAsString(testContent));
            } catch (JsonProcessingException e) {
                log.error("Lỗi serialize JSON đề bài", e);
            }
            todayRecord = recordRepository.save(todayRecord);
        }

        // Đóng gói chuyển xuống FE
        DailyPracticeSessionDto response = new DailyPracticeSessionDto();
        response.setRecordId(todayRecord.getId());
        response.setStatus(todayRecord.getStatus().name());
        response.setTestContent(testContent);
        response.setSavedAnswers(savedAnswers);

        return response;
    }

    // API CHẠY NGẦM: Lưu nháp đáp án lúc đang click
    @Transactional
    public void saveDraft(SaveDraftRequestDto request) {
        User user = SecurityUtils.getCurrentUser();
        LocalDate today = LocalDate.now();

        // Tìm đề đang làm dở hôm nay (nếu có)
        DailyTestRecord record = recordRepository.findFirstByUserIdAndTestDateAndStatus(user.getId(), today, TestRecordStatus.IN_PROGRESS)
                .orElse(null);

        if (record == null) {
            return;
        }

        try {
            record.setUserAnswersJson(objectMapper.writeValueAsString(request.getAnswers()));
            record.setUpdatedAt(LocalDateTime.now());
            recordRepository.save(record);
        } catch (JsonProcessingException e) {
            log.error("Lỗi lưu nháp JSON", e);
        }
    }

    // =========================================================================
    // 2. CHẤM ĐIỂM, LƯU LỊCH SỬ FULL VÀ CẬP NHẬT SM-2
    // =========================================================================
    @Transactional
    public DailyReviewResultResponseDto submitDailyToeicReview(DailyReviewSubmissionRequestDto request) {
        User user = SecurityUtils.getCurrentUser();
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();

        // 1. TÌM VÀ KHÓA RECORD ĐỀ (Lấy cái đang IN_PROGRESS)
        DailyTestRecord record = recordRepository.findFirstByUserIdAndTestDateAndStatus(user.getId(), today, TestRecordStatus.IN_PROGRESS)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài test đang làm dở. Vui lòng tải lại trang!"));
        List<Long> questionIds = request.getAnswers().stream()
                .map(UserAnswerDto::getQuestionId)
                .collect(Collectors.toList());
        Map<Long, Question> questionMap = questionRepository.findAllById(questionIds).stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        List<UserQuestionHistory> historiesToSave = new ArrayList<>();
        List<QuestionReviewDto> results = new ArrayList<>();
        Map<String, UserLearningProgress> progressCache = new HashMap<>();
        Set<String> processedSm2KeysThisSession = new HashSet<>();

        int correctCount = 0;
        int totalEarnedXp = 0;
        int totalQuestions = request.getAnswers().size();

        for (UserAnswerDto answer : request.getAnswers()) {
            Question question = questionMap.get(answer.getQuestionId());
            if (question == null) continue;

            // Xử lý logic Điểm & XP
            boolean isBlank = answer.getSelectedAnswer() == null || answer.getSelectedAnswer().trim().isEmpty();
            boolean isCorrect = !isBlank && question.getCorrectAnswer().equalsIgnoreCase(answer.getSelectedAnswer());

            if (isCorrect) {
                correctCount++;
                totalEarnedXp += 10; // Đúng: +10 XP
            } else if (!isBlank) {
                totalEarnedXp += 2;  // Sai (nhưng có làm): +2 XP
            } else {
                totalEarnedXp += 0;  // Bỏ trống: 0 XP
            }

            // ... (Đoạn ghi lịch sử UserQuestionHistory và Progress SM-2 giữ nguyên không đổi) ...
            UserQuestionHistory history = new UserQuestionHistory();
            history.setUserId(user.getId());
            history.setQuestionId(question.getId());
            history.setCorrect(isCorrect);
            history.setAnsweredAt(now);
            historiesToSave.add(history);

            UUID knowledgeId = question.getKnowledgeItem() != null ? question.getKnowledgeItem().getId() : null;
            String targetWord = question.getTargetWord();

            if (knowledgeId != null || targetWord != null) {
                String cacheKey = ProgressCacheKeyUtil.buildKey(knowledgeId, targetWord);
                if (!processedSm2KeysThisSession.contains(cacheKey)) {
                    UserLearningProgress progress = DailyReviewProgressHelper.resolveOrCreateProgress(
                            user, question, progressCache, progressRepository, question.getToeicPart());
                    spacedRepetitionService.updateProgress(progress, isCorrect);
                    processedSm2KeysThisSession.add(cacheKey);
                }
            }

            results.add(QuestionReviewBuilder.build(
                    question,
                    answer.getSelectedAnswer(),
                    isCorrect,
                    KnowledgeDisplayNameUtil.forDailyReview(question)));
        }

        userQuestionHistoryRepository.saveAll(historiesToSave);
        progressRepository.saveAll(progressCache.values());

        // ========================================================
        // ÁP DỤNG LUẬT 10% CHỐNG SPAM (Lấy từ DB)
        // ========================================================
        int scorePercent = totalQuestions > 0 ? (correctCount * 100) / totalQuestions : 0;

        // Lấy con số 10 từ Database (Nếu không có thì mặc định là 10)
        int minScorePercent = appConfigRepository.findById("MIN_PRACTICE_SCORE_PERCENT")
                .map(config -> Integer.parseInt(config.getConfigValue()))
                .orElse(10);

        boolean isValidEffort = scorePercent >= minScorePercent;

        if (isValidEffort) {
            user.setTotalXp(user.getTotalXp() + totalEarnedXp);
        } else {
            // TƯỚC QUYỀN NHẬN XP
            totalEarnedXp = 0;
            log.warn("User {} nộp bài không đạt chuẩn ({}%). Hủy toàn bộ XP kiếm được.", user.getEmail(), scorePercent);
        }
        userRepository.save(user);

        // 2. CHỐT SỔ ĐỀ THI VÀO DATABASE
        record.setStatus(TestRecordStatus.COMPLETED);
        record.setScore(correctCount);

        // QUAN TRỌNG: Ghi lại Level lúc user làm bài này để sau này check phong độ 7 ngày!
        record.setLevel(user.getCurrentLevel());

        try {
            Map<Long, String> answerMap = request.getAnswers().stream()
                    .collect(Collectors.toMap(UserAnswerDto::getQuestionId, UserAnswerDto::getSelectedAnswer));

            record.setUserAnswersJson(objectMapper.writeValueAsString(answerMap));
            record.setReviewJson(objectMapper.writeValueAsString(results));
        } catch (Exception e) {
            log.error("Lỗi parse JSON chốt sổ", e);
        }
        record.setUpdatedAt(now);
        recordRepository.save(record);

        // Trả thêm isValidEffort và totalEarnedXp về cho Frontend hiện thông báo
        return new DailyReviewResultResponseDto(totalQuestions, correctCount, results, isValidEffort, totalEarnedXp);
    }

    // =========================================================================
    // 3. CÁC HÀM HELPER: LẮP RÁP ĐỀ THI VÀ LẤY LỊCH SỬ
    // =========================================================================

    private List<ToeicPassageResponseDto> buildNewTestBlocks(User user) {
        String userLevel = user.getCurrentLevel().name();

        List<Long> pickedQuestionIds = new ArrayList<>(List.of(-1L));
        List<Long> pickedPassageIds = new ArrayList<>(List.of(-1L));
        List<Question> finalTestQuestions = new ArrayList<>();

        List<UserLearningProgress> dueItems = progressRepository
                .findByUserIdAndNextReviewDateLessThanEqualOrderByIntervalDaysAscNextReviewDateAsc(
                        user.getId(), LocalDateTime.now()
                );

        int part5Count = 0;
        int part6Count = 0;
        int part7SingleCount = 0;
        int part7MultiCount = 0;

        for (UserLearningProgress item : dueItems) {
            ToeicPart part = item.getToeicPart();
            if (part == null) continue;

            UUID kId = item.getKnowledgeItem() != null ? item.getKnowledgeItem().getId() : null;
            String targetWord = item.getTargetWord();

            if (part == ToeicPart.PART_5 && part5Count < 15) {
                Optional<Question> qOpt = questionRepository.findNewPart5Question(kId, targetWord, user.getId(), pickedQuestionIds);
                if (qOpt.isEmpty()) {
                    qOpt = questionRepository.findLruPart5Question(kId, targetWord, user.getId(), pickedQuestionIds);
                }

                if (qOpt.isPresent()) {
                    finalTestQuestions.add(qOpt.get());
                    pickedQuestionIds.add(qOpt.get().getId());
                    part5Count++;
                }
            } else if (part == ToeicPart.PART_6 && part6Count < 2) {
                part6Count += processPassageReview(ToeicPart.PART_6, kId, targetWord, userLevel, user.getId(), pickedQuestionIds, finalTestQuestions, pickedPassageIds, item);
            } else if (part == ToeicPart.PART_7_SINGLE && part7SingleCount < 3) {
                part7SingleCount += processPassageReview(ToeicPart.PART_7_SINGLE, kId, targetWord, userLevel, user.getId(), pickedQuestionIds, finalTestQuestions, pickedPassageIds, item);
            } else if (part == ToeicPart.PART_7_MULTIPLE && part7MultiCount < 3) {
                part7MultiCount += processPassageReview(ToeicPart.PART_7_MULTIPLE, kId, targetWord, userLevel, user.getId(), pickedQuestionIds, finalTestQuestions, pickedPassageIds, item);
            }
        }

        if (part5Count < 15) {
            List<Question> fillP5 = questionRepository.findUnansweredPart5ToFill(userLevel, user.getId(), pickedQuestionIds, 15 - part5Count);
            finalTestQuestions.addAll(fillP5);
        }

        part6Count += fillMissingPassages(ToeicPart.PART_6, user, 2 - part6Count, pickedPassageIds, pickedQuestionIds, finalTestQuestions);
        part7SingleCount += fillMissingPassages(ToeicPart.PART_7_SINGLE, user, 3 - part7SingleCount, pickedPassageIds, pickedQuestionIds, finalTestQuestions);
        part7MultiCount += fillMissingPassages(ToeicPart.PART_7_MULTIPLE, user, 3 - part7MultiCount, pickedPassageIds, pickedQuestionIds, finalTestQuestions);

        return packQuestionsIntoPassageBlocks(finalTestQuestions);
    }

    private int processPassageReview(ToeicPart part, UUID kId, String targetWord, String level, UUID userId, List<Long> pickedQuestionIds, List<Question> finalTestQuestions, List<Long> pickedPassageIds, UserLearningProgress item) {
        Optional<Question> targetQOpt = questionRepository.findNewTargetedPassageQuestion(part.name(), kId, targetWord, level, userId, pickedQuestionIds);

        if (targetQOpt.isEmpty()) {
            targetQOpt = questionRepository.findLruTargetedPassageQuestion(part.name(), kId, targetWord, level, userId, pickedQuestionIds);
        }

        if (targetQOpt.isEmpty()) {
            try {
                dataSyncService.generateAndSaveToeicPassage(Level.valueOf(level), part, item.getKnowledgeItem(), targetWord, Purpose.PRACTICE);
                targetQOpt = questionRepository.findNewTargetedPassageQuestion(part.name(), kId, targetWord, level, userId, pickedQuestionIds);
            } catch (Exception e) {
                log.error("Failed to generate emergency SM-2 passage: {}", e.getMessage(), e);
            }
        }

        if (targetQOpt.isPresent() && targetQOpt.get().getPassage() != null) {
            Passage passage = targetQOpt.get().getPassage();
            if (!pickedPassageIds.contains(passage.getId())) {
                pickedPassageIds.add(passage.getId());
                for (Question q : passage.getQuestions()) {
                    finalTestQuestions.add(q);
                    pickedQuestionIds.add(q.getId());
                }
                return 1;
            }
        }
        return 0;
    }

    private int fillMissingPassages(ToeicPart part, User user, int amountNeeded, List<Long> pickedPassageIds, List<Long> pickedQuestionIds, List<Question> finalTestQuestions) {
        if (amountNeeded <= 0) return 0;
        int filled = 0;

        List<Passage> randomPassages = passageRepository.findUnansweredPassagesToFill(part.name(), user.getId(), pickedPassageIds, amountNeeded);
        for (Passage p : randomPassages) {
            pickedPassageIds.add(p.getId());
            finalTestQuestions.addAll(p.getQuestions());
            p.getQuestions().forEach(q -> pickedQuestionIds.add(q.getId()));
            filled++;
        }

        int aiRetries = 0;
        int MAX_AI_RETRIES = 2;

        while (filled < amountNeeded && aiRetries < MAX_AI_RETRIES) {
            try {
                aiRetries++;
                log.warn("Empty random question pool, invoking real-time AI generation (attempt {})", aiRetries);
                dataSyncService.generateAndSaveToeicPassage(user.getCurrentLevel(), part, null, null, Purpose.PRACTICE);

                List<Passage> freshPassages = passageRepository.findUnansweredPassagesToFill(part.name(), user.getId(), pickedPassageIds, 1);
                if (!freshPassages.isEmpty()) {
                    Passage p = freshPassages.get(0);
                    pickedPassageIds.add(p.getId());
                    finalTestQuestions.addAll(p.getQuestions());
                    p.getQuestions().forEach(q -> pickedQuestionIds.add(q.getId()));
                    filled++;
                }
            } catch (Exception e) {
                log.error("Gemini API error while filling practice test: {}", e.getMessage(), e);
            }
        }

        if (filled < amountNeeded) {
            int remaining = amountNeeded - filled;
            log.warn("AI unavailable, activating fallback: fetching {} oldest passage blocks", remaining);

            List<Passage> oldPassages = passageRepository.findOldestAnsweredPassagesToFill(part.name(), user.getId(), pickedPassageIds, remaining);
            for (Passage p : oldPassages) {
                pickedPassageIds.add(p.getId());
                finalTestQuestions.addAll(p.getQuestions());
                p.getQuestions().forEach(q -> pickedQuestionIds.add(q.getId()));
                filled++;
            }
        }

        return filled;
    }

    private List<ToeicPassageResponseDto> packQuestionsIntoPassageBlocks(List<Question> flatQuestions) {
        List<ToeicPassageResponseDto> packedBlocks = new ArrayList<>();

        List<Question> part5s = flatQuestions.stream().filter(q -> q.getToeicPart() == ToeicPart.PART_5).collect(Collectors.toList());
        if (!part5s.isEmpty()) {
            List<ToeicQuestionResponseDto> dtos = part5s.stream().map(q ->
                    new ToeicQuestionResponseDto(q.getId(), q.getContent(), q.getOptions(), q.getQuestionType())
            ).collect(Collectors.toList());
            packedBlocks.add(new ToeicPassageResponseDto(null, null, ToeicPart.PART_5, dtos));
        }

        Map<Long, Passage> uniquePassages = new LinkedHashMap<>();
        for (Question q : flatQuestions) {
            if (q.getPassage() != null) {
                uniquePassages.putIfAbsent(q.getPassage().getId(), q.getPassage());
            }
        }

        for (Passage p : uniquePassages.values()) {
            List<ToeicQuestionResponseDto> dtos = p.getQuestions().stream().map(q ->
                    new ToeicQuestionResponseDto(q.getId(), q.getContent(), q.getOptions(), q.getQuestionType())
            ).collect(Collectors.toList());

            ToeicPart partLabel = p.getQuestions().isEmpty() ? ToeicPart.PART_6 : p.getQuestions().get(0).getToeicPart();
            packedBlocks.add(new ToeicPassageResponseDto(p.getId(), p.getContent(), partLabel, dtos));
        }

        return packedBlocks;
    }

    @Transactional(readOnly = true)
    public List<DailyPracticeHistoryDto> getPracticeHistory() {
        User user = SecurityUtils.getCurrentUser();
        List<DailyTestRecord> records = recordRepository
                .findByUserIdAndStatusOrderByTestDateDesc(user.getId(), TestRecordStatus.COMPLETED);

        return records.stream().map(record -> {
            DailyPracticeHistoryDto dto = new DailyPracticeHistoryDto();
            dto.setRecordId(record.getId());
            dto.setStatus(record.getStatus().name());
            dto.setTestDate(record.getTestDate().toString());
            dto.setScore(record.getScore());
            dto.setTotalQuestions(record.getTotalQuestions());
            dto.setReviewJson(record.getReviewJson());
            dto.setQuestionsJson(record.getQuestionsJson());
            return dto;
        }).collect(Collectors.toList());
    }
}