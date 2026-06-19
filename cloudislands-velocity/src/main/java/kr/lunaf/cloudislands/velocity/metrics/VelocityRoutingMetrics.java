package kr.lunaf.cloudislands.velocity.metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class VelocityRoutingMetrics {
    private final AtomicLong routeAttempts = new AtomicLong();
    private final AtomicLong routeSuccesses = new AtomicLong();
    private final AtomicLong routeFailures = new AtomicLong();
    private final Map<String, AtomicLong> routeFailureCodes = new ConcurrentHashMap<>();
    private final AtomicLong fallbackTransfers = new AtomicLong();
    private final AtomicLong fallbackMissing = new AtomicLong();
    private final AtomicLong fallbackFailures = new AtomicLong();
    private final AtomicLong fallbackSkippedOffline = new AtomicLong();
    private final AtomicLong pendingRouteLookups = new AtomicLong();
    private final AtomicLong pendingRouteResumes = new AtomicLong();
    private final AtomicLong pendingRouteMissing = new AtomicLong();
    private final AtomicLong pendingRouteFailures = new AtomicLong();
    private volatile String lastFallbackCode = "none";
    private volatile String lastFallbackCategory = "none";
    private volatile String lastFallbackResult = "none";
    private volatile long lastFallbackAtEpochMillis;

    public void routeAttempt() {
        routeAttempts.incrementAndGet();
    }

    public void routeSuccess() {
        routeSuccesses.incrementAndGet();
    }

    public void routeFailure(String code) {
        routeFailures.incrementAndGet();
        routeFailureCodes.computeIfAbsent(safeCode(code), ignored -> new AtomicLong()).incrementAndGet();
    }

    public void fallbackTransfer() {
        fallbackTransfers.incrementAndGet();
    }

    public void fallbackMissing() {
        fallbackMissing.incrementAndGet();
    }

    public void fallbackFailure() {
        fallbackFailures.incrementAndGet();
    }

    public void fallbackSkippedOffline() {
        fallbackSkippedOffline.incrementAndGet();
    }

    public void pendingRouteLookup() {
        pendingRouteLookups.incrementAndGet();
    }

    public void pendingRouteResume() {
        pendingRouteResumes.incrementAndGet();
    }

    public void pendingRouteMissing() {
        pendingRouteMissing.incrementAndGet();
    }

    public void pendingRouteFailure() {
        pendingRouteFailures.incrementAndGet();
    }

    public void rememberFallback(String code, String result) {
        String safeCode = safeCode(code);
        lastFallbackCode = safeCode;
        lastFallbackCategory = kr.lunaf.cloudislands.protocol.route.RouteFailureMessagePolicy.playerSafeCategory(safeCode);
        lastFallbackResult = result == null || result.isBlank() ? "unknown" : result;
        lastFallbackAtEpochMillis = System.currentTimeMillis();
    }

    public String statusSummary() {
        return ", routeAttempts=" + routeAttempts.get()
            + ", routeSuccesses=" + routeSuccesses.get()
            + ", routeFailures=" + routeFailures.get()
            + ", routeFailureCodes=" + routeFailureCodesSummary()
            + ", fallbackTransfers=" + fallbackTransfers.get()
            + ", fallbackMissing=" + fallbackMissing.get()
            + ", fallbackFailures=" + fallbackFailures.get()
            + ", fallbackSkippedOffline=" + fallbackSkippedOffline.get()
            + ", pendingRouteLookups=" + pendingRouteLookups.get()
            + ", pendingRouteResumes=" + pendingRouteResumes.get()
            + ", pendingRouteMissing=" + pendingRouteMissing.get()
            + ", pendingRouteFailures=" + pendingRouteFailures.get()
            + ", lastFallbackCode=" + lastFallbackCode
            + ", lastFallbackCategory=" + lastFallbackCategory
            + ", lastFallbackResult=" + lastFallbackResult
            + ", lastFallbackAtEpochMillis=" + lastFallbackAtEpochMillis;
    }

    public String prometheusText() {
        return ""
            + "cloudislands_velocity_route_attempts_total " + routeAttempts.get() + "\n"
            + "cloudislands_velocity_route_success_total " + routeSuccesses.get() + "\n"
            + "cloudislands_velocity_route_failed_total " + routeFailures.get() + "\n"
            + routeFailureCodeMetrics()
            + "cloudislands_velocity_fallback_transfers_total " + fallbackTransfers.get() + "\n"
            + "cloudislands_velocity_fallback_missing_total " + fallbackMissing.get() + "\n"
            + "cloudislands_velocity_fallback_failed_total " + fallbackFailures.get() + "\n"
            + "cloudislands_velocity_fallback_skipped_offline_total " + fallbackSkippedOffline.get() + "\n"
            + "cloudislands_velocity_pending_route_lookups_total " + pendingRouteLookups.get() + "\n"
            + "cloudislands_velocity_pending_route_resumes_total " + pendingRouteResumes.get() + "\n"
            + "cloudislands_velocity_pending_route_missing_total " + pendingRouteMissing.get() + "\n"
            + "cloudislands_velocity_pending_route_failures_total " + pendingRouteFailures.get() + "\n"
            + "cloudislands_velocity_last_fallback_at_epoch_seconds " + (lastFallbackAtEpochMillis / 1000L) + "\n";
    }

    private String routeFailureCodesSummary() {
        if (routeFailureCodes.isEmpty()) {
            return "none";
        }
        return routeFailureCodes.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> entry.getKey() + "=" + entry.getValue().get())
            .reduce((left, right) -> left + "," + right)
            .orElse("none");
    }

    private String routeFailureCodeMetrics() {
        StringBuilder builder = new StringBuilder();
        routeFailureCodes.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> builder.append("cloudislands_velocity_route_failed_total{code=\"")
                .append(metricLabel(entry.getKey()))
                .append("\"} ")
                .append(entry.getValue().get())
                .append('\n'));
        return builder.toString();
    }

    private static String safeCode(String code) {
        String value = code == null || code.isBlank() ? "ROUTE_FAILED" : code;
        return value.trim().replaceAll("[^A-Za-z0-9_.:-]", "_");
    }

    private static String metricLabel(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
