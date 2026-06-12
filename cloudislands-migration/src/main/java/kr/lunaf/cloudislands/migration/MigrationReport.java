package kr.lunaf.cloudislands.migration;

import java.util.List;

public record MigrationReport(
    int manifests,
    int members,
    int bannedVisitors,
    int homes,
    int warps,
    int flags,
    int permissions,
    int upgrades,
    int limits,
    int completedMissions,
    int blockValues,
    int blockCounts,
    int blockingIssues,
    int warningIssues,
    List<MigrationIssue> issues
) {
    public boolean canImport() {
        return manifests > 0 && blockingIssues == 0;
    }

    public boolean hasIssues() {
        return blockingIssues > 0 || warningIssues > 0;
    }
}
