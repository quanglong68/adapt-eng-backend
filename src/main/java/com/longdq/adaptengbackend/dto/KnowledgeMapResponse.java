package com.longdq.adaptengbackend.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class KnowledgeMapResponse {
    private List<SkillRadarData> radarData;
    private List<WeakPointItem> weakPoints;
    private List<MasteredItem> masteredItems;

    @Data
    @Builder
    public static class SkillRadarData {
        private String skillEnum; // VD: TENSES
        private String skillName; // VD: Thì (Tenses)
        private int score;        // Điểm % (0-100)
    }

    @Data
    @Builder
    public static class WeakPointItem {
        private String id;
        private String name;
        private int score;
        private String lastMistake; // VD: "Hôm qua"
    }

    @Data
    @Builder
    public static class MasteredItem {
        private String id;
        private String name;
        private int score;
        private String lastReview; // VD: "1 tuần trước"
    }
}