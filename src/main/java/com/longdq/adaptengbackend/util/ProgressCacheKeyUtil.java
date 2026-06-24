package com.longdq.adaptengbackend.util;

import java.util.UUID;

public final class ProgressCacheKeyUtil {

    private ProgressCacheKeyUtil() {
    }

    public static String buildKey(UUID knowledgeId, String targetWord) {
        return (knowledgeId != null ? knowledgeId.toString() : "null") + "_"
                + (targetWord != null ? targetWord : "null");
    }
}
