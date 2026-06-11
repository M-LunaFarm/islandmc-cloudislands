package kr.lunaf.cloudislands.coreservice;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.coreservice.profile.PlayerProfileRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;
import kr.lunaf.cloudislands.migration.MigrationIssue;
import kr.lunaf.cloudislands.migration.MigrationManifest;
import kr.lunaf.cloudislands.migration.SuperiorSkyblock2MigrationScanner;
import kr.lunaf.cloudislands.migration.importer.CloudIslandsMigrationImporter;
import kr.lunaf.cloudislands.migration.importer.MigrationImportPlan;
import kr.lunaf.cloudislands.migration.rollback.MigrationRollbackPlan;
import kr.lunaf.cloudislands.migration.rollback.MigrationRollbackService;
import kr.lunaf.cloudislands.migration.superior.MigrationRunState;
import kr.lunaf.cloudislands.migration.verify.MigrationVerifier;

public final class MigrationAdminService {
    private final IslandRepository islands;
    private final IslandMetadataRepository metadata;
    private final PlayerProfileRepository playerProfiles;
    private final SuperiorSkyblock2MigrationScanner scanner = new SuperiorSkyblock2MigrationScanner();
    private final CloudIslandsMigrationImporter importer = new CloudIslandsMigrationImporter();
    private final MigrationVerifier verifier = new MigrationVerifier();
    private final MigrationRollbackService rollback = new MigrationRollbackService();
    private SuperiorSkyblock2MigrationScanner.ScanResult lastScan = new SuperiorSkyblock2MigrationScanner.ScanResult(List.of(), List.of());
    private MigrationImportPlan lastPlan = new MigrationImportPlan(List.of(), List.of());
    private MigrationRollbackPlan lastRollbackPlan;

    public MigrationAdminService(IslandRepository islands, IslandMetadataRepository metadata, PlayerProfileRepository playerProfiles) {
        this.islands = islands;
        this.metadata = metadata;
        this.playerProfiles = playerProfiles;
    }

    public synchronized String scan(String path) {
        String sourcePath = path == null || path.isBlank() ? "plugins/SuperiorSkyblock2" : path;
        lastScan = scanner.scan(Path.of(sourcePath));
        lastPlan = new MigrationImportPlan(lastScan.manifests(), lastScan.issues());
        return "{\"state\":\"" + MigrationRunState.SCANNED + "\",\"path\":\"" + escape(sourcePath) + "\",\"manifests\":" + lastScan.manifests().size() + ",\"issues\":" + issuesJson(lastScan.issues()) + "}";
    }

    public synchronized String dryRun() {
        lastPlan = importer.dryRun(lastScan.manifests());
        MigrationRunState state = lastPlan.canImport() ? MigrationRunState.DRY_RUN_PASSED : MigrationRunState.DRY_RUN_FAILED;
        return "{\"state\":\"" + state + "\",\"manifests\":" + lastPlan.manifests().size() + ",\"canImport\":" + lastPlan.canImport() + ",\"issues\":" + issuesJson(lastPlan.issues()) + "}";
    }

    public synchronized String importLastPlan() {
        if (lastPlan.manifests().isEmpty()) {
            return "{\"state\":\"" + MigrationRunState.DRY_RUN_FAILED + "\",\"imported\":false,\"importedIslands\":0,\"issues\":" + issuesJson(List.of(new MigrationIssue("MIGRATION_PLAN_EMPTY", "run scan and dryrun before import", true))) + "}";
        }
        CloudIslandsMigrationImporter.ImportResult result = importer.importPlan(lastPlan, manifest -> {
            islands.createOwnedIsland(manifest.islandId(), manifest.ownerUuid(), "superiorskyblock2", "Migrated Island");
            metadata.upsertMember(manifest.islandId(), manifest.ownerUuid(), IslandRole.OWNER);
            for (java.util.UUID memberUuid : manifest.members()) {
                if (!memberUuid.equals(manifest.ownerUuid())) {
                    metadata.upsertMember(manifest.islandId(), memberUuid, IslandRole.MEMBER);
                }
            }
            for (java.util.UUID bannedUuid : manifest.bannedVisitors()) {
                metadata.banVisitor(manifest.islandId(), manifest.ownerUuid(), bannedUuid, "Migrated from SuperiorSkyblock2");
            }
            for (kr.lunaf.cloudislands.migration.MigrationHome home : manifest.homes()) {
                metadata.upsertHome(manifest.islandId(), home.name(), new IslandLocation(home.worldName(), home.x(), home.y(), home.z(), home.yaw(), home.pitch()), manifest.ownerUuid());
            }
            for (kr.lunaf.cloudislands.migration.MigrationWarp warp : manifest.warps()) {
                metadata.upsertWarp(manifest.islandId(), warp.name(), new IslandLocation(warp.worldName(), warp.x(), warp.y(), warp.z(), warp.yaw(), warp.pitch()), warp.publicAccess(), manifest.ownerUuid());
            }
            metadata.setPublicAccess(manifest.islandId(), manifest.publicAccess());
            metadata.setLocked(manifest.islandId(), manifest.locked());
            playerProfiles.setPrimaryIsland(manifest.ownerUuid(), manifest.islandId());
        });
        lastRollbackPlan = result.rollbackPlan();
        MigrationRunState state = result.imported() ? MigrationRunState.IMPORTED : MigrationRunState.DRY_RUN_FAILED;
        return "{\"state\":\"" + state + "\",\"imported\":" + result.imported() + ",\"importedIslands\":" + result.importedIslands() + ",\"issues\":" + issuesJson(result.issues()) + ",\"rollbackPlan\":" + rollbackPlanJson(result.rollbackPlan()) + "}";
    }

    public synchronized String verify() {
        List<MigrationManifest> imported = new ArrayList<>();
        for (MigrationManifest manifest : lastScan.manifests()) {
            islands.findById(manifest.islandId())
                .filter(island -> island.ownerUuid().equals(manifest.ownerUuid()))
                .filter(_island -> manifest.members().stream().allMatch(memberUuid -> metadata.isMember(manifest.islandId(), memberUuid)))
                .filter(_island -> manifest.bannedVisitors().stream().allMatch(bannedUuid -> metadata.isBanned(manifest.islandId(), bannedUuid)))
                .filter(_island -> manifest.homes().stream().allMatch(home -> metadata.home(manifest.islandId(), home.name()).isPresent()))
                .filter(_island -> manifest.warps().stream().allMatch(warp -> metadata.warp(manifest.islandId(), warp.name()).isPresent()))
                .filter(_island -> metadata.isPublicAccess(manifest.islandId()) == manifest.publicAccess())
                .filter(_island -> metadata.isLocked(manifest.islandId()) == manifest.locked())
                .ifPresent(_island -> imported.add(manifest));
        }
        MigrationVerifier.VerificationResult result = verifier.verify(lastScan.manifests(), imported);
        MigrationRunState state = result.passed() ? MigrationRunState.VERIFIED : MigrationRunState.VERIFYING;
        return "{\"state\":\"" + state + "\",\"passed\":" + result.passed() + ",\"expected\":" + lastScan.manifests().size() + ",\"imported\":" + imported.size() + ",\"issues\":" + issuesJson(result.issues()) + "}";
    }

    public synchronized String rollbackLastImport() {
        if (lastRollbackPlan == null) {
            return "{\"state\":\"" + MigrationRunState.ROLLED_BACK + "\",\"rolledBack\":false,\"removedIslands\":0,\"issues\":" + issuesJson(List.of(new MigrationIssue("ROLLBACK_PLAN_NOT_FOUND", "no import rollback plan is available", true))) + "}";
        }
        MigrationRollbackService.RollbackResult result = rollback.rollback(lastRollbackPlan, islandId -> {
            IslandSnapshot island = islands.findById(islandId).orElseThrow(() -> new IllegalStateException("island not found"));
            if (!islands.markDeleted(islandId, island.ownerUuid())) {
                throw new IllegalStateException("island was not removed");
            }
            playerProfiles.find(island.ownerUuid()).primaryIslandId()
                .filter(islandId::equals)
                .ifPresent(_current -> playerProfiles.clearPrimaryIsland(island.ownerUuid()));
        });
        return "{\"state\":\"" + MigrationRunState.ROLLED_BACK + "\",\"rolledBack\":" + result.rolledBack() + ",\"removedIslands\":" + result.removedIslands() + ",\"issues\":" + issuesJson(result.issues()) + "}";
    }

    private String rollbackPlanJson(MigrationRollbackPlan plan) {
        if (plan == null) {
            return "null";
        }
        return "{\"runId\":\"" + plan.runId() + "\",\"importedIslandIds\":" + plan.importedIslandIds().size() + ",\"createdAt\":\"" + plan.createdAt() + "\"}";
    }

    private String issuesJson(List<MigrationIssue> issues) {
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (MigrationIssue issue : issues) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append("{\"code\":\"").append(escape(issue.code()))
                .append("\",\"message\":\"").append(escape(issue.message()))
                .append("\",\"blocking\":").append(issue.blocking())
                .append('}');
        }
        return builder.append(']').toString();
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
