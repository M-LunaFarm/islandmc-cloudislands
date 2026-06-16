package kr.lunaf.cloudislands.api.model;

import java.util.List;

public record MigrationRunSnapshot(
    String state,
    String path,
    String manifestPath,
    String reportPath,
    String approvalToken,
    String sourceFingerprint,
    int manifests,
    boolean canImport,
    boolean imported,
    int importedIslands,
    boolean passed,
    int expected,
    boolean rolledBack,
    int removedIslands,
    boolean rollbackPlanAvailable,
    boolean rollbackPlanConsumed,
    int extractedBundles,
    long extractedFiles,
    long extractedBytes,
    int activationTested,
    int activationTestPassed,
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
        this(state, path, "", "", "", "", manifests, canImport, imported, importedIslands, passed, expected, rolledBack, removedIslands, false, false, 0, 0L, 0L, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, issues);
    }

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
        this(state, path, "", "", "", "", manifests, canImport, imported, importedIslands, passed, expected, rolledBack, removedIslands, false, false, 0, 0L, 0L, 0, 0, members, bannedVisitors, homes, warps, flags, permissions, upgrades, limits, completedMissions, blockValues, blockCounts, blockingIssues, warningIssues, issues);
    }

    public boolean hasBlockingIssues() {
        return blockingIssues > 0 || (issues != null && issues.stream().anyMatch(MigrationIssueSnapshot::blocking));
    }

    public boolean activationTestComplete() {
        return activationTested > 0 && activationTested == activationTestPassed;
    }

    public boolean extractionComplete() {
        return manifests > 0 && extractedBundles >= manifests;
    }

    public boolean importComplete() {
        return imported && importedIslands >= manifests && !hasBlockingIssues();
    }

    public boolean verifyComplete() {
        return passed && expected > 0 && activationTestComplete() && !hasBlockingIssues();
    }

    public boolean rollbackReady() {
        return rollbackPlanAvailable && !rollbackPlanConsumed;
    }

    public boolean rollbackComplete() {
        return rolledBack && removedIslands > 0 && rollbackPlanConsumed;
    }
}
