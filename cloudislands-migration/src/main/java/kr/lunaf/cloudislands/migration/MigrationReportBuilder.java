package kr.lunaf.cloudislands.migration;

import java.util.List;

public final class MigrationReportBuilder {
    private MigrationReportBuilder() {}

    public static MigrationReport build(List<MigrationManifest> manifests, List<MigrationIssue> issues) {
        List<MigrationManifest> safeManifests = manifests == null ? List.of() : manifests;
        List<MigrationIssue> safeIssues = issues == null ? List.of() : List.copyOf(issues);
        int blocking = (int) safeIssues.stream().filter(MigrationIssue::blocking).count();
        int warnings = safeIssues.size() - blocking;
        return new MigrationReport(
            safeManifests.size(),
            sum(safeManifests, CountTarget.MEMBERS),
            sum(safeManifests, CountTarget.BANNED_VISITORS),
            sum(safeManifests, CountTarget.HOMES),
            sum(safeManifests, CountTarget.WARPS),
            sum(safeManifests, CountTarget.FLAGS),
            sum(safeManifests, CountTarget.PERMISSIONS),
            sum(safeManifests, CountTarget.UPGRADES),
            sum(safeManifests, CountTarget.LIMITS),
            sum(safeManifests, CountTarget.COMPLETED_MISSIONS),
            sum(safeManifests, CountTarget.BLOCK_VALUES),
            sum(safeManifests, CountTarget.BLOCK_COUNTS),
            blocking,
            warnings,
            safeIssues
        );
    }

    private static int sum(List<MigrationManifest> manifests, CountTarget target) {
        int total = 0;
        for (MigrationManifest manifest : manifests) {
            total += switch (target) {
                case MEMBERS -> manifest.members().size();
                case BANNED_VISITORS -> manifest.bannedVisitors().size();
                case HOMES -> manifest.homes().size();
                case WARPS -> manifest.warps().size();
                case FLAGS -> manifest.flags().size();
                case PERMISSIONS -> manifest.permissions().size();
                case UPGRADES -> manifest.upgrades().size();
                case LIMITS -> manifest.limits().size();
                case COMPLETED_MISSIONS -> manifest.completedMissions().size();
                case BLOCK_VALUES -> manifest.blockValues().size();
                case BLOCK_COUNTS -> manifest.blockCounts().size();
            };
        }
        return total;
    }

    private enum CountTarget {
        MEMBERS,
        BANNED_VISITORS,
        HOMES,
        WARPS,
        FLAGS,
        PERMISSIONS,
        UPGRADES,
        LIMITS,
        COMPLETED_MISSIONS,
        BLOCK_VALUES,
        BLOCK_COUNTS
    }
}
