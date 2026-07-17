package kr.lunaf.cloudislands.coreclient;

import java.util.List;

public record IslandVisitorStatsView(String islandId, long totalVisits, long uniqueVisitors, List<RecentVisitorView> recentVisitors) {
    public IslandVisitorStatsView {
        islandId = islandId == null ? "" : islandId;
        recentVisitors = recentVisitors == null ? List.of() : List.copyOf(recentVisitors);
    }

    public record RecentVisitorView(String visitorUuid, String lastVisitedAt, String visitorName) {
        public RecentVisitorView(String visitorUuid, String lastVisitedAt) {
            this(visitorUuid, lastVisitedAt, "");
        }

        public RecentVisitorView {
            visitorUuid = visitorUuid == null ? "" : visitorUuid;
            lastVisitedAt = lastVisitedAt == null ? "" : lastVisitedAt;
            visitorName = visitorName == null ? "" : visitorName;
        }
    }
}
