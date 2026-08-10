package com.tryna.domain.recommendation.constants;

public final class RecommendationRedisKey {
    private static final String LATEST_REVISION_PREFIX =
            "tryna:recommendation:latest-revision:";

    private RecommendationRedisKey() {
    }

    public static String latestRevision(String tempEventId) {
        return LATEST_REVISION_PREFIX + tempEventId;
    }
}
