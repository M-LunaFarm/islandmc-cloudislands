package kr.lunaf.cloudislands.api.model;

import java.util.List;

public record MigrationRunSnapshot(
    String state,
    String path,
    int manifests,
    boolean canImport,
    boolean imported,
    int importedIslands,
    boolean passed,
    int expected,
    boolean rolledBack,
    int removedIslands,
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
    List<MigrationIssueSnapshot> issues
) {
    public MigrationRunSnapshot(
        String state,
        String path,
        int manifests,
        boolean canImport,
        boolean imported,
        int importedIslands,
        boolean passed,
        int expected,
        boolean rolledBack,
        int removedIslands,
        List<MigrationIssueSnapshot> issues
    ) {
        this(state, path, manifests, canImport, imported, importedIslands, passed, expected, rolledBack, removedIslands, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, issues);
    }
}
