package com.longdq.adaptengbackend.util;

import com.longdq.adaptengbackend.entity.Question;

public final class KnowledgeDisplayNameUtil {

    private KnowledgeDisplayNameUtil() {
    }

    public static String forGeneralEnglish(Question question) {
        if (question.getTargetWord() != null) {
            return "Từ vựng: " + question.getTargetWord();
        }
        if (question.getKnowledgeItem() != null) {
            return question.getKnowledgeItem().getKnowledgeName();
        }
        return "";
    }

    public static String forToeicTest(Question question) {
        if (question.getKnowledgeItem() != null) {
            String name = question.getKnowledgeItem().getKnowledgeName();
            if (question.getTargetWord() != null) {
                return name + " (Từ khóa: " + question.getTargetWord() + ")";
            }
            return name;
        }
        if (question.getTargetWord() != null) {
            return "Từ vựng: " + question.getTargetWord();
        }
        return "";
    }

    public static String forDailyReview(Question question) {
        return forGeneralEnglish(question);
    }
}
