package com.longdq.adaptengbackend.util;

import com.longdq.adaptengbackend.enums.Level;
import lombok.Getter;

public final class TestLevelEvaluationUtil {

    private static final double PASS_THRESHOLD = 70.0;

    private TestLevelEvaluationUtil() {
    }

    @Getter
    public static class LevelEvaluation {
        private final double scorePercentage;
        private final boolean passed;
        private final Level recommendedLevel;
        private final String systemMessage;

        private LevelEvaluation(double scorePercentage, boolean passed, Level recommendedLevel, String systemMessage) {
            this.scorePercentage = scorePercentage;
            this.passed = passed;
            this.recommendedLevel = recommendedLevel;
            this.systemMessage = systemMessage;
        }
    }

    public static LevelEvaluation evaluateUnsafeDivision(int correctCount, int totalQuestions, Level testedLevel) {
        double scorePercentage = (double) correctCount / totalQuestions * 100;
        return buildEvaluation(scorePercentage, testedLevel);
    }

    public static LevelEvaluation evaluateSafeDivision(int correctCount, int totalQuestions, Level testedLevel) {
        double scorePercentage = totalQuestions == 0 ? 0 : (double) correctCount / totalQuestions * 100;
        return buildEvaluation(scorePercentage, testedLevel);
    }

    private static LevelEvaluation buildEvaluation(double scorePercentage, Level testedLevel) {
        boolean passed = scorePercentage >= PASS_THRESHOLD;
        Level recommended = testedLevel;
        String message = "Tuyệt vời! Trình độ của bạn hoàn toàn phù hợp với mức " + recommended + ".";

        if (!passed) {
            int currentOrdinal = testedLevel.ordinal();
            if (currentOrdinal > 0) {
                recommended = Level.values()[currentOrdinal - 1];
                message = "Bài test hơi quá sức. Điểm của bạn là " + String.format("%.1f", scorePercentage)
                        + "%. Chúng tôi khuyên bạn nên củng cố lại nền tảng ở mức " + recommended + " trước nhé.";
            } else {
                message = "Hãy tiếp tục cố gắng ở mức A1 nhé!";
            }
        }

        return new LevelEvaluation(scorePercentage, passed, recommended, message);
    }
}
