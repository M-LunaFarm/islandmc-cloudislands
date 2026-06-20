package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IslandVisitorStatsSnapshot(UUID islandId, long totalVisits, long uniqueVisitors, List<RecentVisitor> recentVisitors) {
    public IslandVisitorStatsSnapshot {
        totalVisits = Math.max(0L, totalVisits);
        uniqueVisitors = Math.max(0L, uniqueVisitors);
        recentVisitors = List.copyOf(recentVisitors == null ? List.of() : recentVisitors);
    }

    public record RecentVisitor(String visitorUuid, Instant lastVisitedAt) {
        public RecentVisitor {
            visitorUuid = visitorUuid == null ? "" : visitorUuid;
            lastVisitedAt = lastVisitedAt == null ? Instant.EPOCH : lastVisitedAt;
        }
    }
}
