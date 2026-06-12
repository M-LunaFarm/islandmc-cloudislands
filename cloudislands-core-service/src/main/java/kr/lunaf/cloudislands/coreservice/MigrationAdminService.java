package kr.lunaf.cloudislands.coreservice;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.coreservice.bank.IslandBankRepository;
import kr.lunaf.cloudislands.coreservice.limit.IslandLimitRepository;
import kr.lunaf.cloudislands.coreservice.mission.IslandMissionRepository;
import kr.lunaf.cloudislands.coreservice.permission.IslandPermissionRuleRepository;
import kr.lunaf.cloudislands.coreservice.profile.PlayerProfileRepository;
import kr.lunaf.cloudislands.coreservice.ranking.IslandLevelRepository;
import kr.lunaf.cloudislands.coreservice.ranking.RankingRecalculationService;
import kr.lunaf.cloudislands.coreservice.repository.IslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;
import kr.lunaf.cloudislands.coreservice.upgrade.IslandUpgradeRepository;
import kr.lunaf.cloudislands.coreservice.upgrade.UpgradePolicy;
import kr.lunaf.cloudislands.migration.MigrationIssue;
import kr.lunaf.cloudislands.migration.MigrationManifest;
import kr.lunaf.cloudislands.migration.MigrationReport;
import kr.lunaf.cloudislands.migration.MigrationReportBuilder;
import kr.lunaf.cloudislands.migration.SuperiorSkyblock2MigrationScanner;
import kr.lunaf.cloudislands.migration.importer.CloudIslandsMigrationImporter;
import kr.lunaf.cloudislands.migration.importer.MigrationImportPlan;
import kr.lunaf.cloudislands.migration.rollback.MigrationRollbackPlan;
import kr.lunaf.cloudislands.migration.rollback.MigrationRollbackService;
import kr.lunaf.cloudislands.migration.rollback.MigrationRollbackService.RollbackTarget;
import kr.lunaf.cloudislands.migration.superior.MigrationRunState;
import kr.lunaf.cloudislands.migration.verify.MigrationVerifier;
import kr.lunaf.cloudislands.migration.world.MigrationWorldBundle;
import kr.lunaf.cloudislands.migration.world.MigrationWorldExtractor;

public final class MigrationAdminService {
    private final IslandRepository islands;
    private final IslandMetadataRepository metadata;
    private final PlayerProfileRepository playerProfiles;
    private final IslandPermissionRuleRepository permissionRules;
    private final IslandUpgradeRepository upgrades;
    private final IslandBankRepository bank;
    private final IslandLimitRepository limits;
    private final IslandMissionRepository missions;
    private final IslandLevelRepository levels;
    private final RollbackTarget hardRollbackTarget;
    private final Path migrationBundleRoot;
    private final SuperiorSkyblock2MigrationScanner scanner = new SuperiorSkyblock2MigrationScanner();
    private final CloudIslandsMigrationImporter importer = new CloudIslandsMigrationImporter();
    private final MigrationVerifier verifier = new MigrationVerifier();
    private final MigrationWorldExtractor worldExtractor = new MigrationWorldExtractor();
    private final MigrationRollbackService rollback = new MigrationRollbackService();
    private SuperiorSkyblock2MigrationScanner.ScanResult lastScan = new SuperiorSkyblock2MigrationScanner.ScanResult(List.of(), List.of());
    private MigrationImportPlan lastPlan = new MigrationImportPlan(List.of(), List.of());
    private MigrationRollbackPlan lastRollbackPlan;

    public MigrationAdminService(IslandRepository islands, IslandMetadataRepository metadata, PlayerProfileRepository playerProfiles, IslandPermissionRuleRepository permissionRules, IslandUpgradeRepository upgrades, IslandBankRepository bank, IslandLimitRepository limits, IslandMissionRepository missions, IslandLevelRepository levels, RollbackTarget hardRollbackTarget) {
        this(islands, metadata, playerProfiles, permissionRules, upgrades, bank, limits, missions, levels, hardRollbackTarget, Path.of("cloudislands-storage"));
    }

    public MigrationAdminService(IslandRepository islands, IslandMetadataRepository metadata, PlayerProfileRepository playerProfiles, IslandPermissionRuleRepository permissionRules, IslandUpgradeRepository upgrades, IslandBankRepository bank, IslandLimitRepository limits, IslandMissionRepository missions, IslandLevelRepository levels, RollbackTarget hardRollbackTarget, Path migrationBundleRoot) {
        this.islands = islands;
        this.metadata = metadata;
        this.playerProfiles = playerProfiles;
        this.permissionRules = permissionRules;
        this.upgrades = upgrades;
        this.bank = bank;
        this.limits = limits;
        this.missions = missions;
        this.levels = levels;
        this.hardRollbackTarget = hardRollbackTarget;
        this.migrationBundleRoot = migrationBundleRoot == null ? Path.of("cloudislands-storage") : migrationBundleRoot;
    }

    public synchronized String scan(String path) {
        String sourcePath = path == null || path.isBlank() ? "plugins/SuperiorSkyblock2" : path;
        lastScan = scanner.scan(Path.of(sourcePath));
        lastPlan = new MigrationImportPlan(lastScan.manifests(), lastScan.issues());
        return "{\"state\":\"" + MigrationRunState.SCANNED + "\",\"path\":\"" + escape(sourcePath) + "\",\"manifests\":" + lastScan.manifests().size() + reportFields(lastPlan.report()) + ",\"issues\":" + issuesJson(lastScan.issues()) + "}";
    }

    public synchronized String dryRun() {
        lastPlan = importer.dryRun(lastScan.manifests());
        MigrationRunState state = lastPlan.canImport() ? MigrationRunState.DRY_RUN_PASSED : MigrationRunState.DRY_RUN_FAILED;
        return "{\"state\":\"" + state + "\",\"manifests\":" + lastPlan.manifests().size() + ",\"canImport\":" + lastPlan.canImport() + reportFields(lastPlan.report()) + ",\"issues\":" + issuesJson(lastPlan.issues()) + "}";
    }

    public synchronized String extractWorldBundles(String outputPath) {
        Path targetRoot = outputPath == null || outputPath.isBlank() ? migrationBundleRoot : Path.of(outputPath);
        List<MigrationIssue> issues = new ArrayList<>();
        int extractedBundles = 0;
        long extractedFiles = 0L;
        long extractedBytes = 0L;
        for (MigrationManifest manifest : lastScan.manifests()) {
            if (manifest.sourceWorldPath() == null || manifest.sourceWorldPath().isBlank()) {
                issues.add(new MigrationIssue("WORLD_SOURCE_NOT_FOUND", "missing world source for " + manifest.islandId(), false));
                continue;
            }
            try {
                MigrationWorldBundle bundle = worldExtractor.extract(worldExtractor.plan(manifest, targetRoot));
                extractedBundles++;
                extractedFiles += bundle.fileCount();
                extractedBytes += bundle.sizeBytes();
            } catch (RuntimeException | java.io.IOException exception) {
                issues.add(new MigrationIssue("WORLD_EXTRACT_FAILED", manifest.islandId() + ": " + exception.getMessage(), true));
            }
        }
        MigrationRunState state = issues.stream().anyMatch(MigrationIssue::blocking) ? MigrationRunState.EXTRACT_FAILED : MigrationRunState.EXTRACTED;
        return "{\"state\":\"" + state + "\",\"path\":\"" + escape(targetRoot.toString()) + "\",\"manifests\":" + lastScan.manifests().size() + ",\"extractedBundles\":" + extractedBundles + ",\"extractedFiles\":" + extractedFiles + ",\"extractedBytes\":" + extractedBytes + reportFields(MigrationReportBuilder.build(lastScan.manifests(), issues)) + ",\"issues\":" + issuesJson(issues) + "}";
    }

    public synchronized String importLastPlan() {
        if (lastPlan.manifests().isEmpty()) {
            List<MigrationIssue> issues = List.of(new MigrationIssue("MIGRATION_PLAN_EMPTY", "run scan and dryrun before import", true));
            return "{\"state\":\"" + MigrationRunState.DRY_RUN_FAILED + "\",\"imported\":false,\"importedIslands\":0" + reportFields(MigrationReportBuilder.build(List.of(), issues)) + ",\"issues\":" + issuesJson(issues) + "}";
        }
        CloudIslandsMigrationImporter.ImportResult result = importer.importPlan(lastPlan, manifest -> {
            islands.createOwnedIsland(manifest.islandId(), manifest.ownerUuid(), "superiorskyblock2", "Migrated Island");
            islands.updateStats(manifest.islandId(), manifest.size(), manifest.level(), manifest.worth());
            metadata.upsertMember(manifest.islandId(), manifest.ownerUuid(), IslandRole.OWNER);
            for (java.util.UUID memberUuid : manifest.members()) {
                if (!memberUuid.equals(manifest.ownerUuid())) {
                    metadata.upsertMember(manifest.islandId(), memberUuid, IslandRole.MEMBER);
                }
            }
            for (kr.lunaf.cloudislands.migration.MigrationMemberRole memberRole : manifest.memberRoles()) {
                if (!memberRole.playerUuid().equals(manifest.ownerUuid())) {
                    metadata.upsertMember(manifest.islandId(), memberRole.playerUuid(), IslandRole.valueOf(memberRole.roleName()));
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
            for (kr.lunaf.cloudislands.migration.MigrationFlag flag : manifest.flags()) {
                metadata.setFlag(manifest.islandId(), IslandFlag.valueOf(flag.flagName()), flag.value());
            }
            for (kr.lunaf.cloudislands.migration.MigrationPermission permission : manifest.permissions()) {
                permissionRules.put(manifest.islandId(), IslandRole.valueOf(permission.roleName()), IslandPermission.valueOf(permission.permissionName()), permission.allowed());
            }
            for (kr.lunaf.cloudislands.migration.MigrationUpgrade upgrade : manifest.upgrades()) {
                upgrades.setLevel(manifest.islandId(), upgrade.upgradeKey(), UpgradePolicy.typeFor(upgrade.upgradeKey()), upgrade.level());
            }
            for (kr.lunaf.cloudislands.migration.MigrationLimit limit : manifest.limits()) {
                limits.set(manifest.islandId(), limit.limitKey(), limit.value(), manifest.ownerUuid());
            }
            for (kr.lunaf.cloudislands.migration.MigrationMission mission : manifest.completedMissions()) {
                missions.importCompleted(manifest.islandId(), manifest.ownerUuid(), mission.missionKey(), mission.kind());
            }
            for (kr.lunaf.cloudislands.migration.MigrationBlockValue value : manifest.blockValues()) {
                levels.putBlockValue(value.materialKey(), new RankingRecalculationService.BlockValue(decimal(value.worth()), value.levelPoints(), value.limit()));
            }
            for (kr.lunaf.cloudislands.migration.MigrationBlockCount count : manifest.blockCounts()) {
                levels.addBlockDelta(manifest.islandId(), count.materialKey(), count.count());
            }
            if (!manifest.biomeKey().isBlank()) {
                metadata.setBiome(manifest.islandId(), manifest.biomeKey(), manifest.ownerUuid());
            }
            BigDecimal bankBalance = decimal(manifest.bankBalance());
            if (bankBalance.signum() > 0) {
                bank.deposit(manifest.islandId(), bankBalance);
            }
            metadata.setPublicAccess(manifest.islandId(), manifest.publicAccess());
            metadata.setLocked(manifest.islandId(), manifest.locked());
            playerProfiles.setPrimaryIsland(manifest.ownerUuid(), manifest.islandId());
        });
        lastRollbackPlan = result.rollbackPlan();
        MigrationRunState state = result.imported() ? MigrationRunState.IMPORTED : MigrationRunState.DRY_RUN_FAILED;
        return "{\"state\":\"" + state + "\",\"imported\":" + result.imported() + ",\"importedIslands\":" + result.importedIslands() + reportFields(MigrationReportBuilder.build(lastPlan.manifests(), result.issues())) + ",\"issues\":" + issuesJson(result.issues()) + ",\"rollbackPlan\":" + rollbackPlanJson(result.rollbackPlan()) + "}";
    }

    public synchronized String verify() {
        List<MigrationManifest> imported = new ArrayList<>();
        List<MigrationIssue> issues = new ArrayList<>();
        for (MigrationManifest manifest : lastScan.manifests()) {
            IslandSnapshot island = islands.findById(manifest.islandId()).orElse(null);
            if (island == null) {
                issues.add(new MigrationIssue("MISSING_IMPORTED_ISLAND", "missing imported island " + manifest.islandId(), true));
                continue;
            }
            boolean matched = true;
            matched &= expect(issues, island.ownerUuid().equals(manifest.ownerUuid()), "OWNER_MISMATCH", "owner mismatch " + manifest.islandId());
            matched &= expect(issues, island.size() == manifest.size(), "SIZE_MISMATCH", "size mismatch " + manifest.islandId());
            matched &= expect(issues, island.level() == manifest.level(), "LEVEL_MISMATCH", "level mismatch " + manifest.islandId());
            matched &= expect(issues, decimal(island.worth()).compareTo(decimal(manifest.worth())) == 0, "WORTH_MISMATCH", "worth mismatch " + manifest.islandId());
            matched &= expect(issues, manifest.members().stream().allMatch(memberUuid -> metadata.isMember(manifest.islandId(), memberUuid)), "MEMBER_MISMATCH", "member mismatch " + manifest.islandId());
            matched &= expect(issues, memberRolesMatch(manifest), "MEMBER_ROLE_MISMATCH", "member role mismatch " + manifest.islandId());
            matched &= expect(issues, manifest.bannedVisitors().stream().allMatch(bannedUuid -> metadata.isBanned(manifest.islandId(), bannedUuid)), "BAN_MISMATCH", "ban mismatch " + manifest.islandId());
            matched &= expect(issues, manifest.homes().stream().allMatch(home -> metadata.home(manifest.islandId(), home.name()).isPresent()), "HOME_MISMATCH", "home mismatch " + manifest.islandId());
            matched &= expect(issues, manifest.warps().stream().allMatch(warp -> metadata.warp(manifest.islandId(), warp.name()).isPresent()), "WARP_MISMATCH", "warp mismatch " + manifest.islandId());
            matched &= expect(issues, manifest.flags().stream().allMatch(flag -> flag.value().equals(metadata.flags(manifest.islandId()).values().get(IslandFlag.valueOf(flag.flagName())))), "FLAG_MISMATCH", "flag mismatch " + manifest.islandId());
            matched &= expect(issues, permissionsMatch(manifest), "PERMISSION_MISMATCH", "permission mismatch " + manifest.islandId());
            matched &= expect(issues, upgradesMatch(manifest), "UPGRADE_MISMATCH", "upgrade mismatch " + manifest.islandId());
            matched &= expect(issues, limitsMatch(manifest), "LIMIT_MISMATCH", "limit mismatch " + manifest.islandId());
            matched &= expect(issues, missionsMatch(manifest), "MISSION_MISMATCH", "mission mismatch " + manifest.islandId());
            matched &= expect(issues, blockValuesMatch(manifest), "BLOCK_VALUE_MISMATCH", "block value mismatch " + manifest.islandId());
            matched &= expect(issues, blockCountsMatch(manifest), "BLOCK_COUNT_MISMATCH", "block count mismatch " + manifest.islandId());
            matched &= expect(issues, manifest.biomeKey().isBlank() || metadata.biome(manifest.islandId()).biomeKey().equals(manifest.biomeKey()), "BIOME_MISMATCH", "biome mismatch " + manifest.islandId());
            matched &= expect(issues, decimal(bank.balance(manifest.islandId()).balance()).compareTo(decimal(manifest.bankBalance())) == 0, "BANK_MISMATCH", "bank mismatch " + manifest.islandId());
            matched &= expect(issues, metadata.isPublicAccess(manifest.islandId()) == manifest.publicAccess(), "PUBLIC_ACCESS_MISMATCH", "public access mismatch " + manifest.islandId());
            matched &= expect(issues, metadata.isLocked(manifest.islandId()) == manifest.locked(), "LOCKED_MISMATCH", "locked mismatch " + manifest.islandId());
            if (matched) {
                imported.add(manifest);
            }
        }
        MigrationVerifier.VerificationResult result = verifier.verify(lastScan.manifests(), imported);
        issues.addAll(result.issues());
        boolean passed = issues.isEmpty();
        MigrationRunState state = passed ? MigrationRunState.VERIFIED : MigrationRunState.VERIFYING;
        return "{\"state\":\"" + state + "\",\"passed\":" + passed + ",\"expected\":" + lastScan.manifests().size() + ",\"imported\":" + imported.size() + reportFields(MigrationReportBuilder.build(lastScan.manifests(), issues)) + ",\"issues\":" + issuesJson(issues) + "}";
    }

    public synchronized String rollbackLastImport() {
        if (lastRollbackPlan == null) {
            return "{\"state\":\"" + MigrationRunState.ROLLED_BACK + "\",\"rolledBack\":false,\"removedIslands\":0,\"issues\":" + issuesJson(List.of(new MigrationIssue("ROLLBACK_PLAN_NOT_FOUND", "no import rollback plan is available", true))) + "}";
        }
        MigrationRollbackService.RollbackResult result = rollback.rollback(lastRollbackPlan, islandId -> {
            IslandSnapshot island = islands.findById(islandId).orElseThrow(() -> new IllegalStateException("island not found"));
            if (hardRollbackTarget != null) {
                hardRollbackTarget.removeImportedIsland(islandId);
            } else if (!islands.markDeleted(islandId, island.ownerUuid())) {
                throw new IllegalStateException("island was not removed");
            }
            playerProfiles.find(island.ownerUuid()).primaryIslandId()
                .filter(islandId::equals)
                .ifPresent(_current -> playerProfiles.clearPrimaryIsland(island.ownerUuid()));
        });
        return "{\"state\":\"" + MigrationRunState.ROLLED_BACK + "\",\"rolledBack\":" + result.rolledBack() + ",\"removedIslands\":" + result.removedIslands() + ",\"issues\":" + issuesJson(result.issues()) + "}";
    }

    private String reportFields(MigrationReport report) {
        return ",\"members\":" + report.members()
            + ",\"bannedVisitors\":" + report.bannedVisitors()
            + ",\"homes\":" + report.homes()
            + ",\"warps\":" + report.warps()
            + ",\"flags\":" + report.flags()
            + ",\"permissions\":" + report.permissions()
            + ",\"upgrades\":" + report.upgrades()
            + ",\"limits\":" + report.limits()
            + ",\"completedMissions\":" + report.completedMissions()
            + ",\"blockValues\":" + report.blockValues()
            + ",\"blockCounts\":" + report.blockCounts()
            + ",\"blockingIssues\":" + report.blockingIssues()
            + ",\"warningIssues\":" + report.warningIssues();
    }

    private boolean permissionsMatch(MigrationManifest manifest) {
        Map<String, Boolean> current = new java.util.HashMap<>();
        for (kr.lunaf.cloudislands.api.model.IslandPermissionRuleSnapshot rule : permissionRules.list(manifest.islandId())) {
            current.put(rule.role().name() + ":" + rule.permission().name(), rule.allowed());
        }
        return manifest.permissions().stream().allMatch(permission -> Boolean.valueOf(permission.allowed()).equals(current.get(permission.roleName() + ":" + permission.permissionName())));
    }

    private boolean memberRolesMatch(MigrationManifest manifest) {
        Map<java.util.UUID, IslandRole> current = new java.util.HashMap<>();
        for (kr.lunaf.cloudislands.api.model.IslandMemberSnapshot member : metadata.members(manifest.islandId())) {
            current.put(member.playerUuid(), member.role());
        }
        return manifest.memberRoles().stream().allMatch(memberRole -> IslandRole.valueOf(memberRole.roleName()).equals(current.get(memberRole.playerUuid())));
    }

    private boolean upgradesMatch(MigrationManifest manifest) {
        Map<String, Integer> current = new java.util.HashMap<>();
        for (kr.lunaf.cloudislands.api.upgrade.IslandUpgradeSnapshot upgrade : upgrades.list(manifest.islandId())) {
            current.put(upgrade.upgradeKey(), upgrade.level());
        }
        return manifest.upgrades().stream().allMatch(upgrade -> Integer.valueOf(upgrade.level()).equals(current.get(upgrade.upgradeKey())));
    }

    private boolean limitsMatch(MigrationManifest manifest) {
        Map<String, Long> current = new java.util.HashMap<>();
        for (kr.lunaf.cloudislands.api.model.IslandLimitSnapshot limit : limits.list(manifest.islandId())) {
            current.put(limit.limitKey(), limit.value());
        }
        return manifest.limits().stream().allMatch(limit -> Long.valueOf(limit.value()).equals(current.get(limit.limitKey())));
    }

    private boolean missionsMatch(MigrationManifest manifest) {
        Map<String, Boolean> current = new java.util.HashMap<>();
        for (kr.lunaf.cloudislands.api.model.IslandMissionSnapshot mission : missions.list(manifest.islandId(), "MISSION")) {
            current.put(mission.missionKey(), mission.completed());
        }
        for (kr.lunaf.cloudislands.api.model.IslandMissionSnapshot mission : missions.list(manifest.islandId(), "CHALLENGE")) {
            current.put(mission.missionKey(), mission.completed());
        }
        return manifest.completedMissions().stream().allMatch(mission -> Boolean.TRUE.equals(current.get(mission.missionKey())));
    }

    private boolean blockValuesMatch(MigrationManifest manifest) {
        Map<String, RankingRecalculationService.BlockValue> current = levels.blockValues();
        return manifest.blockValues().stream().allMatch(value -> {
            RankingRecalculationService.BlockValue currentValue = current.get(value.materialKey());
            return currentValue != null
                && currentValue.worth().compareTo(decimal(value.worth())) == 0
                && currentValue.levelPoints() == value.levelPoints()
                && currentValue.limit() == value.limit();
        });
    }

    private boolean blockCountsMatch(MigrationManifest manifest) {
        Map<String, Long> current = levels.blockCounts(manifest.islandId());
        return manifest.blockCounts().stream().allMatch(count -> Long.valueOf(count.count()).equals(current.get(count.materialKey())));
    }

    private boolean expect(List<MigrationIssue> issues, boolean passed, String code, String message) {
        if (!passed) {
            issues.add(new MigrationIssue(code, message, true));
        }
        return passed;
    }

    private BigDecimal decimal(String value) {
        try {
            return new BigDecimal(value == null || value.isBlank() ? "0.00" : value);
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
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
