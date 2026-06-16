package kr.lunaf.cloudislands.migration.verify;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import kr.lunaf.cloudislands.migration.MigrationIssue;
import kr.lunaf.cloudislands.migration.MigrationManifest;
import kr.lunaf.cloudislands.migration.MigrationReport;
import kr.lunaf.cloudislands.migration.MigrationReportBuilder;

public final class MigrationVerifier {
    public VerificationResult verify(List<MigrationManifest> expected, List<MigrationManifest> imported) {
        List<MigrationIssue> issues = new ArrayList<>();
        if (expected.size() != imported.size()) {
            issues.add(new MigrationIssue("COUNT_MISMATCH", "expected " + expected.size() + " imported islands but found " + imported.size(), true));
        }
        Set<UUID> importedIds = new HashSet<>();
        Map<UUID, MigrationManifest> importedById = new HashMap<>();
        for (MigrationManifest manifest : imported) {
            if (!importedIds.add(manifest.islandId())) {
                issues.add(new MigrationIssue("DUPLICATE_IMPORTED_ID", "imported island ids contain duplicates", true));
            } else {
                importedById.put(manifest.islandId(), manifest);
            }
        }
        for (MigrationManifest manifest : expected) {
            MigrationManifest actual = importedById.get(manifest.islandId());
            if (actual == null) {
                issues.add(new MigrationIssue("MISSING_IMPORTED_ISLAND", "missing imported island " + manifest.islandId(), true));
                continue;
            }
            compareManifest(manifest, actual, issues);
        }
        return new VerificationResult(issues.isEmpty(), issues, MigrationReportBuilder.build(imported, issues));
    }

    private void compareManifest(MigrationManifest expected, MigrationManifest actual, List<MigrationIssue> issues) {
        UUID islandId = expected.islandId();
        expectEquals(issues, expected.ownerUuid(), actual.ownerUuid(), "OWNER_MISMATCH", "owner mismatch " + islandId);
        expectEquals(issues, expected.members(), actual.members(), "MEMBERS_MISMATCH", "members mismatch " + islandId);
        expectEquals(issues, expected.memberRoles(), actual.memberRoles(), "ROLES_MISMATCH", "roles mismatch " + islandId);
        expectEquals(issues, expected.bannedVisitors(), actual.bannedVisitors(), "BANS_MISMATCH", "visitor bans mismatch " + islandId);
        expectEquals(issues, expected.homes(), actual.homes(), "HOMES_MISMATCH", "homes mismatch " + islandId);
        expectEquals(issues, expected.warps(), actual.warps(), "WARPS_MISMATCH", "warps mismatch " + islandId);
        expectEquals(issues, expected.flags(), actual.flags(), "FLAGS_MISMATCH", "flags mismatch " + islandId);
        expectEquals(issues, expected.permissions(), actual.permissions(), "PERMISSIONS_MISMATCH", "permissions mismatch " + islandId);
        expectEquals(issues, expected.upgrades(), actual.upgrades(), "UPGRADES_MISMATCH", "upgrades mismatch " + islandId);
        expectEquals(issues, expected.limits(), actual.limits(), "LIMITS_MISMATCH", "limits mismatch " + islandId);
        expectEquals(issues, expected.completedMissions(), actual.completedMissions(), "MISSIONS_MISMATCH", "completed missions mismatch " + islandId);
        expectEquals(issues, expected.blockValues(), actual.blockValues(), "BLOCK_VALUES_MISMATCH", "block values mismatch " + islandId);
        expectEquals(issues, expected.blockCounts(), actual.blockCounts(), "BLOCK_COUNTS_MISMATCH", "block counts mismatch " + islandId);
        expectEquals(issues, expected.biomeKey(), actual.biomeKey(), "BIOME_MISMATCH", "biome mismatch " + islandId);
        expectEquals(issues, expected.bankBalance(), actual.bankBalance(), "BANK_BALANCE_MISMATCH", "bank balance mismatch " + islandId);
        expectEquals(issues, expected.publicAccess(), actual.publicAccess(), "PUBLIC_ACCESS_MISMATCH", "public access mismatch " + islandId);
        expectEquals(issues, expected.locked(), actual.locked(), "LOCKED_MISMATCH", "locked state mismatch " + islandId);
        expectEquals(issues, expected.size(), actual.size(), "SIZE_MISMATCH", "size mismatch " + islandId);
        expectEquals(issues, expected.level(), actual.level(), "LEVEL_MISMATCH", "level mismatch " + islandId);
        expectEquals(issues, expected.worth(), actual.worth(), "WORTH_MISMATCH", "worth mismatch " + islandId);
        expectEquals(issues, expected.islandLocation(), actual.islandLocation(), "LOCATION_MISMATCH", "island location mismatch " + islandId);
        expectEquals(issues, expected.sourceWorldPath(), actual.sourceWorldPath(), "SOURCE_WORLD_MISMATCH", "source world mismatch " + islandId);
    }

    private void expectEquals(List<MigrationIssue> issues, Object expected, Object actual, String code, String message) {
        if (!Objects.equals(expected, actual)) {
            issues.add(new MigrationIssue(code, message, true));
        }
    }

    public record VerificationResult(boolean passed, List<MigrationIssue> issues, MigrationReport report) {
        public VerificationResult(boolean passed, List<MigrationIssue> issues) {
            this(passed, issues, MigrationReportBuilder.build(List.of(), issues));
        }
    }
}
