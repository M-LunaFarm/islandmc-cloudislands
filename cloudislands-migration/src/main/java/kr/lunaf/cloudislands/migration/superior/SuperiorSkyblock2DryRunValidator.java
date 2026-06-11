package kr.lunaf.cloudislands.migration.superior;

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
        }
        return issues;
    }
}
