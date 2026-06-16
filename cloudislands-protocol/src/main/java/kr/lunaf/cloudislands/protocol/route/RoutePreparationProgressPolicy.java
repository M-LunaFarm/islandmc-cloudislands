package kr.lunaf.cloudislands.protocol.route;

public final class RoutePreparationProgressPolicy {
    public static final String CONTRACT = "route-preparing-actionbar-and-bossbar-hide-node-names";
    public static final int PREPARING_INITIAL_PERCENT = 20;
    public static final int PREPARING_STEP_PERCENT = 4;
    public static final int PREPARING_MAX_PERCENT = 95;
    public static final int HANDOFF_INITIAL_PERCENT = 10;
    public static final int HANDOFF_MAX_PERCENT = 95;
    public static final int HANDOFF_EXPECTED_ATTEMPTS = 20;

    private RoutePreparationProgressPolicy() {
    }

    public static int preparingPercent(int attempt) {
        return boundedPercent(PREPARING_INITIAL_PERCENT + Math.max(0, attempt) * PREPARING_STEP_PERCENT);
    }

    public static float preparingProgress(int attempt) {
        return preparingPercent(attempt) / 100.0F;
    }

    public static float handoffProgress(int attempt) {
        int safeAttempt = Math.max(0, attempt);
        float range = HANDOFF_MAX_PERCENT - HANDOFF_INITIAL_PERCENT;
        float percent = HANDOFF_INITIAL_PERCENT + (range * safeAttempt / HANDOFF_EXPECTED_ATTEMPTS);
        return boundedPercent(Math.round(percent)) / 100.0F;
    }

    private static int boundedPercent(int percent) {
        return Math.max(0, Math.min(PREPARING_MAX_PERCENT, percent));
    }
}
