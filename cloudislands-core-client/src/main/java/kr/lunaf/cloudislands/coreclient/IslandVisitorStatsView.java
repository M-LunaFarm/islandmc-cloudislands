package kr.lunaf.cloudislands.coreclient;

import java.util.List;

public record IslandVisitorStatsView(String islandId, long totalVisits, long uniqueVisitors, List<RecentVisitorView> recentVisitors) {
    public IslandVisitorStatsView {
        islandId = islandId == null ? "" : islandId;
        recentVisitors = recentVisitors == null ? List.of() : List.copyOf(recentVisitors);
    }

    public record RecentVisitorView(String visitorUuid, String lastVisitedAt) {
        public RecentVisitorView {
            visitorUuid = visitorUuid == null ? "" : visitorUuid;
            lastVisitedAt = lastVisitedAt == null ? "" : lastVisitedAt;
        }
    }
}
