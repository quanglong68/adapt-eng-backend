package com.longdq.adaptengbackend.service;

import com.longdq.adaptengbackend.dto.KnowledgeMapResponse;
import com.longdq.adaptengbackend.entity.User;
import com.longdq.adaptengbackend.repository.UserQuestionHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KnowledgeMapService {

    private final UserQuestionHistoryRepository historyRepository;

    // HÀM TIỆN ÍCH: Dịch thời gian sang Tiếng Việt
    private String getTimeAgo(Timestamp timestamp) {
        if (timestamp == null) return "Chưa rõ";
        LocalDateTime time = timestamp.toLocalDateTime();
        long days = Duration.between(time, LocalDateTime.now()).toDays();

        if (days == 0) return "Hôm nay";
        if (days == 1) return "Hôm qua";
        if (days <= 7) return days + " ngày trước";
        if (days <= 30) return (days / 7) + " tuần trước";
        return (days / 30) + " tháng trước";
    }

    // HÀM TIỆN ÍCH: Dịch Enum sang Tiếng Việt cho Đồ thị Radar
    // HÀM TIỆN ÍCH: Dịch và Gom nhóm Enum sang Tiếng Việt cho Đồ thị Radar (6 Trục)
    private String translateEnumToName(String enumStr) {
        return switch (enumStr) {
            // Nhóm 1: Trục CÁC THÌ (Tenses) - Dàn 12 thì
            case "PRESENT_SIMPLE", "PRESENT_CONTINUOUS", "PRESENT_PERFECT", "PRESENT_PERFECT_CONTINUOUS",
                 "PAST_SIMPLE", "PAST_CONTINUOUS", "PAST_PERFECT", "PAST_PERFECT_CONTINUOUS",
                 "FUTURE_SIMPLE", "FUTURE_CONTINUOUS", "FUTURE_PERFECT", "FUTURE_PERFECT_CONTINUOUS"
                    -> "Các Thì (Tenses)";

            // Nhóm 2: Trục TỪ LOẠI (Parts of Speech) - Tách ra từ Ngữ pháp
            case "PRONOUNS", "NOUNS_AND_QUANTIFIERS", "ADJECTIVES_AND_ADVERBS",
                 "PREPOSITIONS", "ARTICLES", "CONJUNCTIONS"
                    -> "Từ loại (Parts of Speech)";

            // Nhóm 3: Trục CẤU TRÚC CÂU (Sentence Structures) - Tách ra từ Ngữ pháp
            case "CONDITIONAL_SENTENCES", "RELATIVE_CLAUSES", "PASSIVE_VOICE",
                 "REPORTED_SPEECH", "CLAUSES"
                    -> "Cấu trúc câu (Sentence Structures)";

            // Nhóm 4: Trục ĐỘNG TỪ & CÚ PHÁP (Verbs & Syntax) - Tách ra từ Ngữ pháp
            case "MODAL_VERBS", "GERUNDS_AND_INFINITIVES", "SUBJECT_VERB_AGREEMENT",
                 "COMPARISONS", "WORD_ORDER", "QUESTION_TAGS"
                    -> "Động từ & Cú pháp (Verbs & Syntax)";

            // Nhóm 5: Trục TỪ VỰNG (Vocabulary)
            case "VOCABULARY", "PHRASAL_VERBS", "IDIOMS", "COLLOCATIONS", "WORD_FORMATION"
                    -> "Từ vựng (Vocabulary)";

            // Nhóm 6: Trục KỸ NĂNG (Skills)
            case "PRONUNCIATION", "READING_COMPREHENSION"
                    -> "Kỹ năng phát âm và đọc";

            // Cột thu lôi: Bắt lỗi nếu dưới Database có dữ liệu lạ không khớp với Enum
            default -> "Kiến thức khác";
        };
    }

    // API CHÍNH: Lấy dữ liệu Bản đồ kiến thức
    public KnowledgeMapResponse getKnowledgeMap() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        // 1. Xử lý dữ liệu Radar (Biểu đồ lưới)
        List<UserQuestionHistoryRepository.RadarStatsProjection> radarProjections = historyRepository.findRadarStatsByUserId(user.getId());
        List<KnowledgeMapResponse.SkillRadarData> radarData = radarProjections.stream()
                .map(p -> {
                    int score = p.getTotalQuestions() > 0 ? (int) Math.round((double) p.getCorrectAnswers() / p.getTotalQuestions() * 100) : 0;
                    return KnowledgeMapResponse.SkillRadarData.builder()
                            .skillEnum(p.getSkillEnum())
                            .skillName(translateEnumToName(p.getSkillEnum()))
                            .score(score)
                            .build();
                })
                .collect(Collectors.groupingBy(KnowledgeMapResponse.SkillRadarData::getSkillName,
                        Collectors.averagingInt(KnowledgeMapResponse.SkillRadarData::getScore)))
                .entrySet().stream()
                .map(e -> KnowledgeMapResponse.SkillRadarData.builder()
                        .skillEnum(e.getKey())
                        .skillName(e.getKey())
                        .score((int) Math.round(e.getValue()))
                        .build())
                .collect(Collectors.toList());

        // 2. Xử lý dữ liệu Chi tiết (Lỗ hổng & Thông thạo)
        List<UserQuestionHistoryRepository.DetailStatsProjection> detailProjections = historyRepository.findDetailStatsByUserId(user.getId());

        List<KnowledgeMapResponse.WeakPointItem> weakPoints = new ArrayList<>();
        List<KnowledgeMapResponse.MasteredItem> masteredItems = new ArrayList<>();

        for (UserQuestionHistoryRepository.DetailStatsProjection p : detailProjections) {
            // Cần ít nhất 3 câu hỏi mới đủ cơ sở dữ liệu để đánh giá điểm yếu/thông thạo
            if (p.getTotalQuestions() < 3) continue;

            int score = (int) Math.round((double) p.getCorrectAnswers() / p.getTotalQuestions() * 100);

            // Phân loại LỖ HỔNG (Dưới 60%)
            if (score < 60) {
                weakPoints.add(KnowledgeMapResponse.WeakPointItem.builder()
                        .id(p.getId())
                        .name(p.getSkillName())
                        .score(score)
                        .lastMistake(getTimeAgo(p.getLastMistakeAt()))
                        .build());
            }
            // Phân loại THÔNG THẠO (Từ 80% trở lên)
            else if (score >= 80) {
                masteredItems.add(KnowledgeMapResponse.MasteredItem.builder()
                        .id(p.getId())
                        .name(p.getSkillName())
                        .score(score)
                        .lastReview(getTimeAgo(p.getLastReviewAt()))
                        .build());
            }
        }

        // Sắp xếp: Lỗ hổng ưu tiên điểm thấp nhất. Thông thạo ưu tiên điểm cao nhất.
        weakPoints.sort(Comparator.comparingInt(KnowledgeMapResponse.WeakPointItem::getScore));
        masteredItems.sort(Comparator.comparingInt(KnowledgeMapResponse.MasteredItem::getScore).reversed());

        // Giới hạn hiển thị Top 5 lỗ hổng nghiêm trọng nhất
        if (weakPoints.size() > 5) weakPoints = weakPoints.subList(0, 5);

        return KnowledgeMapResponse.builder()
                .radarData(radarData)
                .weakPoints(weakPoints)
                .masteredItems(masteredItems)
                .build();
    }
}