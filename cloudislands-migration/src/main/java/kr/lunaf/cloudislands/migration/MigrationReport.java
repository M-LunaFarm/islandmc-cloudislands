package kr.lunaf.cloudislands.migration;

import java.util.List;

public record MigrationReport(
    int manifests,
    int members,
    int memberRoles,
    int bannedVisitors,
    int homes,
    int warps,
    int islandLocations,
    int sourceWorlds,
    int islandSizes,
    int levels,
    int worthValues,
    int biomes,
    int bankBalances,
    int flags,
    int permissions,
    int upgrades,
    int limits,
    int completedMissions,
    int blockValues,
    int blockCounts,
    int warehouseItems,
    int blockingIssues,
    int warningIssues,
    int importableIslandCount,
    int ownerMissingCount,
    int worldPathMissingCount,
    int homeMissingCount,
    int warpMissingCount,
    int homeConversionFailureCount,
    int warpConversionFailureCount,
    int permissionConversionFailureCount,
    int unknownFlagCount,
    int blockValueConversionFailureCount,
    int bankEconomyConversionFailureCount,
    int worldBundleChecksumFailureCount,
    int cloudIslandsPostImportDifferenceCount,
    int unsupportedFieldCount,
    boolean rollbackPossible,
    List<MigrationIssue> issues
) {
    public int totalIslands() {
        return manifests;
    }

    public boolean canImport() {
        return manifests > 0 && blockingIssues == 0;
    }

    public boolean hasIssues() {
        return blockingIssues > 0 || warningIssues > 0;
    }

    public boolean manifestGenerated() {
        return manifests > 0;
    }

    public boolean hasConflicts() {
        return issues.stream().anyMatch(MigrationReport::isConflictIssue);
    }

    public int conflictIssues() {
        return (int) issues.stream().filter(MigrationReport::isConflictIssue).count();
    }

    public String manifestStatus() {
        return manifestGenerated() ? "GENERATED" : "MISSING";
    }

    public String conflictStatus() {
        if (conflictIssues() == 0) {
            return "NONE";
        }
        return issues.stream().anyMatch(issue -> isConflictIssue(issue) && issue.blocking()) ? "BLOCKING" : "WARNING";
    }

    private static boolean isConflictIssue(MigrationIssue issue) {
        if (issue == null || issue.code() == null) {
            return false;
        }
        String code = issue.code();
        return code.contains("DUPLICATE")
            || code.contains("CONFLICT")
            || code.contains("COLLISION")
            || code.contains("EXISTS");
    }
}
