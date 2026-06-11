package kr.lunaf.cloudislands.migration.superior;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kr.lunaf.cloudislands.migration.MigrationIssue;
import kr.lunaf.cloudislands.migration.MigrationManifest;

public final class SuperiorSkyblock2DryRunValidator {
    public List<MigrationIssue> validate(List<MigrationManifest> manifests) {
        List<MigrationIssue> issues = new ArrayList<>();
        Set<UUID> owners = new HashSet<>();
        Set<UUID> islands = new HashSet<>();
        for (MigrationManifest manifest : manifests) {
            if (!islands.add(manifest.islandId())) {
                issues.add(new MigrationIssue("DUPLICATE_ISLAND_ID", "duplicate island id " + manifest.islandId(), true));
            }
            if (!owners.add(manifest.ownerUuid())) {
                issues.add(new MigrationIssue("DUPLICATE_OWNER", "owner has multiple source islands " + manifest.ownerUuid(), true));
            }
            if (manifest.size() <= 0) {
                issues.add(new MigrationIssue("INVALID_SIZE", "island has invalid size " + manifest.islandId(), true));
            }
            if (manifest.level() < 0L) {
                issues.add(new MigrationIssue("INVALID_LEVEL", "island has negative level " + manifest.islandId(), true));
            }
            if (!validDecimal(manifest.worth())) {
                issues.add(new MigrationIssue("INVALID_WORTH", "island has invalid worth " + manifest.islandId(), true));
            }
            if (!validDecimal(manifest.bankBalance())) {
                issues.add(new MigrationIssue("INVALID_BANK_BALANCE", "island has invalid bank balance " + manifest.islandId(), true));
            }
            manifest.blockValues().stream()
                .filter(value -> !validDecimal(value.worth()) || value.levelPoints() < 0L || value.limit() < 0L)
                .forEach(value -> issues.add(new MigrationIssue("INVALID_BLOCK_VALUE", "invalid block value " + value.materialKey() + " in " + manifest.islandId(), true)));
            manifest.blockCounts().stream()
                .filter(count -> count.count() < 0L)
                .forEach(count -> issues.add(new MigrationIssue("INVALID_BLOCK_COUNT", "negative block count " + count.materialKey() + " in " + manifest.islandId(), true)));
            manifest.limits().stream()
                .filter(limit -> limit.value() < 0L)
                .forEach(limit -> issues.add(new MigrationIssue("INVALID_LIMIT", "negative limit " + limit.limitKey() + " in " + manifest.islandId(), true)));
            manifest.upgrades().stream()
                .filter(upgrade -> upgrade.level() < 0)
                .forEach(upgrade -> issues.add(new MigrationIssue("INVALID_UPGRADE", "negative upgrade " + upgrade.upgradeKey() + " in " + manifest.islandId(), true)));
        }
        return issues;
    }

    private boolean validDecimal(String value) {
        try {
            return new BigDecimal(value == null || value.isBlank() ? "0.00" : value).signum() >= 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
