package kr.lunaf.cloudislands.migration;

import java.util.List;
import java.util.function.Predicate;

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
            sum(safeManifests, CountTarget.MEMBER_ROLES),
            sum(safeManifests, CountTarget.BANNED_VISITORS),
            sum(safeManifests, CountTarget.HOMES),
            sum(safeManifests, CountTarget.WARPS),
            sum(safeManifests, CountTarget.ISLAND_LOCATIONS),
            sum(safeManifests, CountTarget.SOURCE_WORLDS),
            sum(safeManifests, CountTarget.ISLAND_SIZES),
            sum(safeManifests, CountTarget.LEVELS),
            sum(safeManifests, CountTarget.WORTH_VALUES),
            sum(safeManifests, CountTarget.BIOMES),
            sum(safeManifests, CountTarget.BANK_BALANCES),
            sum(safeManifests, CountTarget.FLAGS),
            sum(safeManifests, CountTarget.PERMISSIONS),
            sum(safeManifests, CountTarget.UPGRADES),
            sum(safeManifests, CountTarget.LIMITS),
            sum(safeManifests, CountTarget.COMPLETED_MISSIONS),
            sum(safeManifests, CountTarget.BLOCK_VALUES),
            sum(safeManifests, CountTarget.BLOCK_COUNTS),
            blocking,
            warnings,
            issueCount(safeIssues, "OWNER_NOT_FOUND"),
            missingCount(safeManifests, MissingTarget.HOMES),
            missingCount(safeManifests, MissingTarget.WARPS),
            issueCount(safeIssues, MigrationReportBuilder::permissionConversionFailure),
            issueCount(safeIssues, MigrationReportBuilder::unknownFlag),
            issueCount(safeIssues, MigrationReportBuilder::worldBundleChecksumFailure),
            issueCount(safeIssues, MigrationReportBuilder::cloudIslandsPostImportDifference),
            rollbackPossible(safeManifests, safeIssues),
            safeIssues
        );
    }

    private static int sum(List<MigrationManifest> manifests, CountTarget target) {
        int total = 0;
        for (MigrationManifest manifest : manifests) {
            total += switch (target) {
                case MEMBERS -> manifest.members().size();
                case MEMBER_ROLES -> manifest.memberRoles().size();
                case BANNED_VISITORS -> manifest.bannedVisitors().size();
                case HOMES -> manifest.homes().size();
                case WARPS -> manifest.warps().size();
                case ISLAND_LOCATIONS -> manifest.islandLocation() != null && manifest.islandLocation().present() ? 1 : 0;
                case SOURCE_WORLDS -> manifest.sourceWorldPath() != null && !manifest.sourceWorldPath().isBlank() ? 1 : 0;
                case ISLAND_SIZES -> manifest.size() > 0 ? 1 : 0;
                case LEVELS -> manifest.level() >= 0L ? 1 : 0;
                case WORTH_VALUES -> manifest.worth() != null && !manifest.worth().isBlank() ? 1 : 0;
                case BIOMES -> manifest.biomeKey() != null && !manifest.biomeKey().isBlank() ? 1 : 0;
                case BANK_BALANCES -> manifest.bankBalance() != null && !manifest.bankBalance().isBlank() ? 1 : 0;
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

    private static int missingCount(List<MigrationManifest> manifests, MissingTarget target) {
        int total = 0;
        for (MigrationManifest manifest : manifests) {
            boolean missing = switch (target) {
                case HOMES -> manifest.homes().isEmpty();
                case WARPS -> manifest.warps().isEmpty();
            };
            if (missing) {
                total++;
            }
        }
        return total;
    }

    private static int issueCount(List<MigrationIssue> issues, String exactCode) {
        return issueCount(issues, code -> code.equals(exactCode));
    }

    private static int issueCount(List<MigrationIssue> issues, Predicate<String> predicate) {
        int total = 0;
        for (MigrationIssue issue : issues) {
            if (issue != null && issue.code() != null && predicate.test(issue.code())) {
                total++;
            }
        }
        return total;
    }

    private static boolean permissionConversionFailure(String code) {
        return code.contains("PERMISSION")
            && (code.contains("CONVERSION") || code.contains("UNKNOWN") || code.contains("INVALID") || code.contains("MISMATCH"));
    }

    private static boolean unknownFlag(String code) {
        return code.contains("FLAG") && (code.contains("UNKNOWN") || code.contains("UNSUPPORTED") || code.contains("INVALID"));
    }

    private static boolean worldBundleChecksumFailure(String code) {
        return code.contains("CHECKSUM");
    }

    private static boolean cloudIslandsPostImportDifference(String code) {
        return code.endsWith("_MISMATCH")
            || code.equals("COUNT_MISMATCH")
            || code.equals("MISSING_IMPORTED_ISLAND")
            || code.equals("INVALID_IMPORTED_MANIFEST")
            || code.equals("DUPLICATE_IMPORTED_ID");
    }

    private static boolean rollbackPossible(List<MigrationManifest> manifests, List<MigrationIssue> issues) {
        return !manifests.isEmpty() && issues.stream().noneMatch(MigrationReportBuilder::rollbackBlockingIssue);
    }

    private static boolean rollbackBlockingIssue(MigrationIssue issue) {
        return issue != null
            && issue.blocking()
            && issue.code() != null
            && issue.code().startsWith("ROLLBACK_");
    }

    private enum CountTarget {
        MEMBERS,
        MEMBER_ROLES,
        BANNED_VISITORS,
        HOMES,
        WARPS,
        ISLAND_LOCATIONS,
        SOURCE_WORLDS,
        ISLAND_SIZES,
        LEVELS,
        WORTH_VALUES,
        BIOMES,
        BANK_BALANCES,
        FLAGS,
        PERMISSIONS,
        UPGRADES,
        LIMITS,
        COMPLETED_MISSIONS,
        BLOCK_VALUES,
        BLOCK_COUNTS
    }

    private enum MissingTarget {
        HOMES,
        WARPS
    }
}
