package kr.lunaf.cloudislands.coreservice;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.model.IslandSnapshotRecord;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.IslandState;
import kr.lunaf.cloudislands.coreservice.bank.IslandBankRepository;
import kr.lunaf.cloudislands.coreservice.limit.IslandLimitRepository;
import kr.lunaf.cloudislands.coreservice.mission.IslandMissionRepository;
import kr.lunaf.cloudislands.coreservice.permission.IslandPermissionRuleRepository;
import kr.lunaf.cloudislands.coreservice.profile.PlayerProfileRepository;
import kr.lunaf.cloudislands.coreservice.ranking.IslandLevelRepository;
import kr.lunaf.cloudislands.coreservice.ranking.RankingRecalculationService;
import kr.lunaf.cloudislands.coreservice.repository.IslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.snapshot.IslandSnapshotRepository;
import kr.lunaf.cloudislands.coreservice.upgrade.IslandUpgradeRepository;
import kr.lunaf.cloudislands.coreservice.upgrade.UpgradePolicy;
import kr.lunaf.cloudislands.coreservice.workflow.IslandLifecycleWorkflow;
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
    private final IslandSnapshotRepository snapshots;
    private final RollbackTarget hardRollbackTarget;
    private final IslandRuntimeRepository runtimes;
    private final Path migrationBundleRoot;
    private final IslandLifecycleWorkflow activationTester;
    private final SuperiorSkyblock2MigrationScanner scanner = new SuperiorSkyblock2MigrationScanner();
    private final CloudIslandsMigrationImporter importer = new CloudIslandsMigrationImporter();
    private final MigrationVerifier verifier = new MigrationVerifier();
    private final MigrationWorldExtractor worldExtractor = new MigrationWorldExtractor();
    private final MigrationRollbackService rollback = new MigrationRollbackService();
    private SuperiorSkyblock2MigrationScanner.ScanResult lastScan = new SuperiorSkyblock2MigrationScanner.ScanResult(List.of(), List.of());
    private MigrationImportPlan lastPlan = new MigrationImportPlan(List.of(), List.of());
    private MigrationRollbackPlan lastRollbackPlan;
    private Path lastExtractionRoot;
    private String lastApprovalToken = "";

    public MigrationAdminService(IslandRepository islands, IslandMetadataRepository metadata, PlayerProfileRepository playerProfiles, IslandPermissionRuleRepository permissionRules, IslandUpgradeRepository upgrades, IslandBankRepository bank, IslandLimitRepository limits, IslandMissionRepository missions, IslandLevelRepository levels, IslandSnapshotRepository snapshots, RollbackTarget hardRollbackTarget) {
        this(islands, metadata, playerProfiles, permissionRules, upgrades, bank, limits, missions, levels, snapshots, hardRollbackTarget, Path.of("cloudislands-storage"));
    }

    public MigrationAdminService(IslandRepository islands, IslandMetadataRepository metadata, PlayerProfileRepository playerProfiles, IslandPermissionRuleRepository permissionRules, IslandUpgradeRepository upgrades, IslandBankRepository bank, IslandLimitRepository limits, IslandMissionRepository missions, IslandLevelRepository levels, IslandSnapshotRepository snapshots, RollbackTarget hardRollbackTarget, Path migrationBundleRoot) {
        this(islands, metadata, playerProfiles, permissionRules, upgrades, bank, limits, missions, levels, snapshots, hardRollbackTarget, migrationBundleRoot, null);
    }

    public MigrationAdminService(IslandRepository islands, IslandMetadataRepository metadata, PlayerProfileRepository playerProfiles, IslandPermissionRuleRepository permissionRules, IslandUpgradeRepository upgrades, IslandBankRepository bank, IslandLimitRepository limits, IslandMissionRepository missions, IslandLevelRepository levels, IslandSnapshotRepository snapshots, RollbackTarget hardRollbackTarget, Path migrationBundleRoot, IslandLifecycleWorkflow activationTester) {
        this(islands, metadata, playerProfiles, permissionRules, upgrades, bank, limits, missions, levels, snapshots, hardRollbackTarget, null, migrationBundleRoot, activationTester);
    }

    public MigrationAdminService(IslandRepository islands, IslandMetadataRepository metadata, PlayerProfileRepository playerProfiles, IslandPermissionRuleRepository permissionRules, IslandUpgradeRepository upgrades, IslandBankRepository bank, IslandLimitRepository limits, IslandMissionRepository missions, IslandLevelRepository levels, IslandSnapshotRepository snapshots, RollbackTarget hardRollbackTarget, IslandRuntimeRepository runtimes, Path migrationBundleRoot, IslandLifecycleWorkflow activationTester) {
        this.islands = islands;
        this.metadata = metadata;
        this.playerProfiles = playerProfiles;
        this.permissionRules = permissionRules;
        this.upgrades = upgrades;
        this.bank = bank;
        this.limits = limits;
        this.missions = missions;
        this.levels = levels;
        this.snapshots = snapshots;
        this.hardRollbackTarget = hardRollbackTarget;
        this.runtimes = runtimes;
        this.migrationBundleRoot = migrationBundleRoot == null ? Path.of("cloudislands-storage") : migrationBundleRoot;
        this.activationTester = activationTester;
        this.lastExtractionRoot = this.migrationBundleRoot;
    }

    public synchronized String scan(String path) {
        String sourcePath = path == null || path.isBlank() ? "plugins/SuperiorSkyblock2" : path;
        lastScan = scanner.scan(Path.of(sourcePath));
        lastApprovalToken = "";
        List<MigrationIssue> issues = new ArrayList<>(lastScan.issues());
        Path manifestPath = migrationManifestPath();
        try {
            writeMigrationManifestFile(sourcePath, manifestPath, lastScan.manifests());
        } catch (java.io.IOException exception) {
            issues.add(new MigrationIssue("MIGRATION_MANIFEST_WRITE_FAILED", exception.getMessage(), true));
        }
        lastScan = new SuperiorSkyblock2MigrationScanner.ScanResult(lastScan.manifests(), List.copyOf(issues));
        lastPlan = new MigrationImportPlan(lastScan.manifests(), lastScan.issues());
        return "{\"state\":\"" + MigrationRunState.SCANNED + "\"" + migrationBoundaryFields() + ",\"path\":\"" + escape(sourcePath) + "\",\"manifestPath\":\"" + escape(manifestPath.toString()) + "\",\"manifests\":" + lastScan.manifests().size() + reportFields(lastPlan.report()) + ",\"issues\":" + issuesJson(lastScan.issues()) + "}";
    }

    public synchronized String dryRun() {
        MigrationImportPlan sourcePlan = importer.dryRun(lastScan.manifests());
        List<MigrationIssue> issues = new ArrayList<>(lastScan.issues());
        issues.addAll(sourcePlan.issues());
        issues.addAll(targetConflictIssues(lastScan.manifests()));
        lastPlan = new MigrationImportPlan(lastScan.manifests(), issues);
        MigrationRunState state = lastPlan.canImport() ? MigrationRunState.DRY_RUN_PASSED : MigrationRunState.DRY_RUN_FAILED;
        lastApprovalToken = lastPlan.canImport() ? java.util.UUID.randomUUID().toString() : "";
        Path reportPath = migrationReportPath("dryrun");
        try {
            writeMigrationReportFile(state.name(), reportPath, lastPlan.report());
        } catch (java.io.IOException exception) {
            issues.add(new MigrationIssue("MIGRATION_REPORT_WRITE_FAILED", exception.getMessage(), true));
            lastPlan = new MigrationImportPlan(lastScan.manifests(), issues);
            state = MigrationRunState.DRY_RUN_FAILED;
            lastApprovalToken = "";
        }
        return "{\"state\":\"" + state + "\"" + migrationBoundaryFields() + ",\"reportPath\":\"" + escape(reportPath.toString()) + "\",\"manifests\":" + lastPlan.manifests().size() + ",\"canImport\":" + lastPlan.canImport() + ",\"approvalRequired\":" + lastPlan.canImport() + (lastApprovalToken.isBlank() ? "" : ",\"approvalToken\":\"" + lastApprovalToken + "\"") + reportFields(lastPlan.report()) + ",\"issues\":" + issuesJson(lastPlan.issues()) + "}";
    }

    public synchronized String status() {
        boolean rollbackPlanAvailable = lastRollbackPlan != null;
        return "{\"state\":\"STATUS\""
            + migrationBoundaryFields()
            + rollbackSafetyFields(rollbackPlanAvailable)
            + ",\"sourcePlugin\":\"SuperiorSkyblock2\""
            + ",\"migrationInputOnly\":true"
            + ",\"runtimeDependency\":false"
            + ",\"operations\":\"scan,dryrun,extract,import,verify,rollback,status\""
            + ",\"scanManifests\":" + lastScan.manifests().size()
            + ",\"planManifests\":" + lastPlan.manifests().size()
            + ",\"canImport\":" + lastPlan.canImport()
            + ",\"approvalRequired\":" + (lastPlan.canImport() && !lastApprovalToken.isBlank())
            + ",\"approvalTokenAvailable\":" + !lastApprovalToken.isBlank()
            + ",\"rollbackPlanAvailable\":" + rollbackPlanAvailable
            + ",\"rollbackPlan\":" + rollbackPlanJson(lastRollbackPlan)
            + ",\"migrationBundleRoot\":\"" + escape(migrationBundleRoot.toString()) + "\""
            + ",\"lastExtractionRoot\":\"" + escape((lastExtractionRoot == null ? migrationBundleRoot : lastExtractionRoot).toString()) + "\""
            + ",\"manifestPath\":\"" + escape(migrationManifestPath().toString()) + "\""
            + ",\"dryrunReportPath\":\"" + escape(migrationReportPath("dryrun").toString()) + "\""
            + ",\"verifyReportPath\":\"" + escape(migrationReportPath("verify").toString()) + "\""
            + reportFields(lastPlan.report())
            + ",\"issues\":" + issuesJson(lastPlan.issues())
            + "}";
    }

    private List<MigrationIssue> targetConflictIssues(List<MigrationManifest> manifests) {
        List<MigrationIssue> issues = new ArrayList<>();
        for (MigrationManifest manifest : manifests) {
            islands.findById(manifest.islandId())
                .ifPresent(_existing -> issues.add(new MigrationIssue("TARGET_ISLAND_EXISTS", "target already has island " + manifest.islandId(), true)));
            islands.findByOwner(manifest.ownerUuid())
                .ifPresent(existing -> issues.add(new MigrationIssue("TARGET_OWNER_HAS_ISLAND", "owner " + manifest.ownerUuid() + " already owns island " + existing.islandId(), true)));
            playerProfiles.find(manifest.ownerUuid()).primaryIslandId()
                .ifPresent(primaryIslandId -> issues.add(new MigrationIssue("TARGET_OWNER_PRIMARY_ISLAND_EXISTS", "owner " + manifest.ownerUuid() + " already has primary island " + primaryIslandId, true)));
        }
        return issues;
    }

    private MigrationWorldBundle verifyMigrationBundle(MigrationManifest manifest) {
        try {
            Path root = lastExtractionRoot == null ? migrationBundleRoot : lastExtractionRoot;
            return worldExtractor.verify(worldExtractor.plan(manifest, root));
        } catch (RuntimeException | java.io.IOException exception) {
            throw new IllegalStateException("migration bundle unavailable for " + manifest.islandId() + ": " + exception.getMessage(), exception);
        }
    }

    private BundlePreflight preflightMigrationBundles(List<MigrationManifest> manifests) {
        Map<java.util.UUID, MigrationWorldBundle> bundles = new HashMap<>();
        List<MigrationIssue> issues = new ArrayList<>();
        for (MigrationManifest manifest : manifests) {
            try {
                bundles.put(manifest.islandId(), verifyMigrationBundle(manifest));
            } catch (RuntimeException exception) {
                issues.add(new MigrationIssue("MIGRATION_BUNDLE_PREFLIGHT_FAILED", manifest.islandId() + ": " + exception.getMessage(), true));
            }
        }
        return new BundlePreflight(bundles, issues);
    }

    private void recordMigrationBundleSnapshot(MigrationManifest manifest, MigrationWorldBundle bundle) {
        if (snapshots == null) {
            return;
        }
        try {
            Path root = lastExtractionRoot == null ? migrationBundleRoot : lastExtractionRoot;
            String storagePath = root.normalize().relativize(bundle.bundlePath().normalize()).toString().replace('\\', '/');
            snapshots.record(manifest.islandId(), 1L, storagePath, "Migrated from SuperiorSkyblock2", manifest.ownerUuid(), bundle.checksum(), bundle.sizeBytes());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("migration bundle snapshot unavailable for " + manifest.islandId() + ": " + exception.getMessage(), exception);
        }
    }

    private boolean snapshotRecordMatches(List<MigrationIssue> issues, MigrationManifest manifest, MigrationWorldBundle bundle, Path root) {
        if (snapshots == null) {
            return true;
        }
        Optional<IslandSnapshotRecord> record = snapshots.find(manifest.islandId(), 1L);
        if (record.isEmpty()) {
            issues.add(new MigrationIssue("MIGRATION_SNAPSHOT_MISSING", "missing migration snapshot record " + manifest.islandId(), true));
            return false;
        }
        IslandSnapshotRecord snapshot = record.get();
        boolean matched = true;
        String storagePath = root.normalize().relativize(bundle.bundlePath().normalize()).toString().replace('\\', '/');
        matched &= expect(issues, storagePath.equals(snapshot.storagePath()), "MIGRATION_SNAPSHOT_PATH_MISMATCH", "snapshot path mismatch " + manifest.islandId());
        matched &= expect(issues, bundle.checksum().equalsIgnoreCase(snapshot.checksum()), "MIGRATION_SNAPSHOT_CHECKSUM_MISMATCH", "snapshot checksum mismatch " + manifest.islandId());
        matched &= expect(issues, bundle.sizeBytes() == snapshot.sizeBytes(), "MIGRATION_SNAPSHOT_SIZE_MISMATCH", "snapshot size mismatch " + manifest.islandId());
        return matched;
    }

    public synchronized String extractWorldBundles(String outputPath) {
        if (lastScan.manifests().isEmpty()) {
            List<MigrationIssue> issues = List.of(new MigrationIssue("MIGRATION_SCAN_REQUIRED", "run scan before extracting SuperiorSkyblock2 worlds", true));
            return "{\"state\":\"" + MigrationRunState.DRY_RUN_FAILED + "\"" + migrationBoundaryFields() + ",\"path\":\"\",\"manifests\":0,\"extractedBundles\":0,\"extractedFiles\":0,\"extractedBytes\":0" + reportFields(MigrationReportBuilder.build(List.of(), issues)) + ",\"issues\":" + issuesJson(issues) + "}";
        }
        Path targetRoot = resolveMigrationBundleRoot(outputPath);
        lastExtractionRoot = targetRoot;
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
        return "{\"state\":\"" + state + "\"" + migrationBoundaryFields() + ",\"path\":\"" + escape(targetRoot.toString()) + "\",\"manifests\":" + lastScan.manifests().size() + ",\"extractedBundles\":" + extractedBundles + ",\"extractedFiles\":" + extractedFiles + ",\"extractedBytes\":" + extractedBytes + reportFields(MigrationReportBuilder.build(lastScan.manifests(), issues)) + ",\"issues\":" + issuesJson(issues) + "}";
    }

    private String migrationBoundaryFields() {
        return ",\"sourcePlugin\":\"SuperiorSkyblock2\",\"migrationInputOnly\":true,\"runtimeDependency\":false,\"targetRuntime\":\"CloudIslands\"";
    }

    public synchronized String importLastPlan() {
        return importLastPlan("");
    }

    public synchronized String importLastPlan(String approvalToken) {
        if (lastPlan.manifests().isEmpty()) {
            List<MigrationIssue> issues = List.of(new MigrationIssue("MIGRATION_PLAN_EMPTY", "run scan and dryrun before import", true));
            return "{\"state\":\"" + MigrationRunState.DRY_RUN_FAILED + "\"" + migrationBoundaryFields() + ",\"imported\":false,\"importedIslands\":0" + reportFields(MigrationReportBuilder.build(List.of(), issues)) + ",\"issues\":" + issuesJson(issues) + "}";
        }
        if (lastApprovalToken.isBlank() || approvalToken == null || !lastApprovalToken.equals(approvalToken.trim())) {
            List<MigrationIssue> issues = List.of(new MigrationIssue("MIGRATION_APPROVAL_REQUIRED", "run dryrun and pass the returned approval token to import", true));
            return "{\"state\":\"" + MigrationRunState.DRY_RUN_PASSED + "\"" + migrationBoundaryFields() + ",\"imported\":false,\"importedIslands\":0,\"approvalRequired\":true" + reportFields(MigrationReportBuilder.build(lastPlan.manifests(), issues)) + ",\"issues\":" + issuesJson(issues) + "}";
        }
        long[] extractedStats = new long[] {0L, 0L, 0L};
        BundlePreflight preflight = preflightMigrationBundles(lastPlan.manifests());
        if (!preflight.issues().isEmpty()) {
            return "{\"state\":\"" + MigrationRunState.DRY_RUN_FAILED + "\"" + migrationBoundaryFields() + ",\"imported\":false,\"importedIslands\":0,\"extractedBundles\":0,\"extractedFiles\":0,\"extractedBytes\":0" + reportFields(MigrationReportBuilder.build(lastPlan.manifests(), preflight.issues())) + ",\"issues\":" + issuesJson(preflight.issues()) + "}";
        }
        Map<java.util.UUID, MigrationWorldBundle> preflightBundles = preflight.bundles();
        CloudIslandsMigrationImporter.ImportResult result = importer.importPlan(lastPlan, manifest -> {
            MigrationWorldBundle bundle = preflightBundles.get(manifest.islandId());
            if (bundle == null) {
                throw new IllegalStateException("migration bundle preflight missing for " + manifest.islandId());
            }
            islands.createOwnedIsland(manifest.islandId(), manifest.ownerUuid(), "superiorskyblock2", "Migrated Island");
            if (runtimes != null) {
                runtimes.markInactive(manifest.islandId());
            }
            islands.setState(manifest.islandId(), IslandState.INACTIVE_READY);
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
            if (manifest.islandLocation().present()) {
                kr.lunaf.cloudislands.migration.MigrationLocation location = manifest.islandLocation();
                metadata.upsertHome(manifest.islandId(), "origin", new IslandLocation(location.worldName(), location.x(), location.y(), location.z(), location.yaw(), location.pitch()), manifest.ownerUuid());
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
            recordMigrationBundleSnapshot(manifest, bundle);
            extractedStats[0]++;
            extractedStats[1] += bundle.fileCount();
            extractedStats[2] += bundle.sizeBytes();
            playerProfiles.setPrimaryIsland(manifest.ownerUuid(), manifest.islandId());
        });
        lastApprovalToken = "";
        lastRollbackPlan = result.rollbackPlan();
        MigrationRunState state = result.imported() ? MigrationRunState.IMPORTED : MigrationRunState.DRY_RUN_FAILED;
        return "{\"state\":\"" + state + "\"" + migrationBoundaryFields() + ",\"imported\":" + result.imported() + ",\"importedIslands\":" + result.importedIslands() + ",\"extractedBundles\":" + extractedStats[0] + ",\"extractedFiles\":" + extractedStats[1] + ",\"extractedBytes\":" + extractedStats[2] + reportFields(MigrationReportBuilder.build(lastPlan.manifests(), result.issues())) + ",\"issues\":" + issuesJson(result.issues()) + ",\"rollbackPlan\":" + rollbackPlanJson(result.rollbackPlan()) + "}";
    }

    private record BundlePreflight(Map<java.util.UUID, MigrationWorldBundle> bundles, List<MigrationIssue> issues) {}

    public synchronized String verify() {
        return verify("");
    }

    public synchronized String verify(String bundleRootPath) {
        if (lastScan.manifests().isEmpty()) {
            List<MigrationIssue> issues = List.of(new MigrationIssue("MIGRATION_SCAN_REQUIRED", "run scan before verifying SuperiorSkyblock2 migration", true));
            return "{\"state\":\"" + MigrationRunState.VERIFYING + "\"" + migrationBoundaryFields() + ",\"path\":\"\",\"reportPath\":\"\",\"passed\":false,\"expected\":0,\"imported\":0,\"extractedBundles\":0,\"extractedFiles\":0,\"extractedBytes\":0,\"activationTested\":0,\"activationTestPassed\":0" + reportFields(MigrationReportBuilder.build(List.of(), issues)) + ",\"issues\":" + issuesJson(issues) + "}";
        }
        List<MigrationManifest> imported = new ArrayList<>();
        List<MigrationIssue> issues = new ArrayList<>();
        Path verifyBundleRoot = bundleRootPath == null || bundleRootPath.isBlank()
            ? (lastExtractionRoot == null ? migrationBundleRoot : lastExtractionRoot)
            : resolveMigrationBundleRoot(bundleRootPath);
        int extractedBundles = 0;
        long extractedFiles = 0L;
        long extractedBytes = 0L;
        int activationTested = 0;
        int activationTestPassed = 0;
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
            matched &= expect(issues, !manifest.islandLocation().present() || originLocationMatches(manifest), "ISLAND_LOCATION_MISMATCH", "island location mismatch " + manifest.islandId());
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
            boolean worldBundleVerified = false;
            if (manifest.sourceWorldPath() == null || manifest.sourceWorldPath().isBlank()) {
                issues.add(new MigrationIssue("WORLD_SOURCE_NOT_FOUND", "missing world source for " + manifest.islandId(), false));
            } else {
                try {
                    MigrationWorldBundle bundle = worldExtractor.verify(worldExtractor.plan(manifest, verifyBundleRoot));
                    matched &= snapshotRecordMatches(issues, manifest, bundle, verifyBundleRoot);
                    extractedBundles++;
                    extractedFiles += bundle.fileCount();
                    extractedBytes += bundle.sizeBytes();
                    worldBundleVerified = true;
                } catch (RuntimeException | java.io.IOException exception) {
                    issues.add(new MigrationIssue("WORLD_CHECKSUM_FAILED", manifest.islandId() + ": " + exception.getMessage(), true));
                }
            }
            if (activationTester == null) {
                activationTested++;
                if (matched && worldBundleVerified) {
                    activationTestPassed++;
                } else {
                    matched &= expect(issues, false, "ACTIVATION_PREFLIGHT_FAILED", "activation preflight failed " + manifest.islandId());
                }
            } else {
                activationTested++;
                IslandLifecycleWorkflow.Result activation = activationTester.activationPreflight(manifest.islandId());
                if (activation.accepted()) {
                    activationTestPassed++;
                } else {
                    matched &= expect(issues, false, "ACTIVATION_TEST_FAILED", "activation test failed " + manifest.islandId() + " code=" + activation.code());
                }
            }
            if (matched) {
                imported.add(manifest);
            }
        }
        MigrationVerifier.VerificationResult result = verifier.verify(lastScan.manifests(), imported);
        issues.addAll(result.issues());
        boolean passed = issues.isEmpty();
        MigrationRunState state = passed ? MigrationRunState.VERIFIED : MigrationRunState.VERIFYING;
        Path reportPath = migrationReportPath("verify");
        try {
            writeMigrationReportFile(state.name(), reportPath, MigrationReportBuilder.build(lastScan.manifests(), issues));
        } catch (java.io.IOException exception) {
            issues.add(new MigrationIssue("MIGRATION_REPORT_WRITE_FAILED", exception.getMessage(), true));
            passed = false;
            state = MigrationRunState.VERIFYING;
        }
        return "{\"state\":\"" + state + "\"" + migrationBoundaryFields() + ",\"path\":\"" + escape(verifyBundleRoot.toString()) + "\",\"reportPath\":\"" + escape(reportPath.toString()) + "\",\"passed\":" + passed + ",\"expected\":" + lastScan.manifests().size() + ",\"imported\":" + imported.size() + ",\"extractedBundles\":" + extractedBundles + ",\"extractedFiles\":" + extractedFiles + ",\"extractedBytes\":" + extractedBytes + ",\"activationTested\":" + activationTested + ",\"activationTestPassed\":" + activationTestPassed + reportFields(MigrationReportBuilder.build(lastScan.manifests(), issues)) + ",\"issues\":" + issuesJson(issues) + "}";
    }

    public synchronized String rollbackLastImport() {
        if (lastRollbackPlan == null) {
            return "{\"state\":\"" + MigrationRunState.ROLLED_BACK + "\"" + migrationBoundaryFields() + rollbackSafetyFields(false) + ",\"rollbackPlanAvailable\":false,\"rolledBack\":false,\"removedIslands\":0,\"issues\":" + issuesJson(List.of(new MigrationIssue("ROLLBACK_PLAN_NOT_FOUND", "no import rollback plan is available", true))) + "}";
        }
        MigrationRollbackPlan plan = lastRollbackPlan;
        MigrationRollbackService.RollbackResult result = rollback.rollback(plan, islandId -> {
            IslandSnapshot island = islands.findById(islandId).orElseThrow(() -> new IllegalStateException("island not found"));
            if (hardRollbackTarget != null) {
                hardRollbackTarget.removeImportedIsland(islandId);
            } else if (!islands.markDeleted(islandId, island.ownerUuid())) {
                throw new IllegalStateException("island was not removed");
            }
            playerProfiles.find(island.ownerUuid()).primaryIslandId()
                .filter(islandId::equals)
                .ifPresent(_current -> playerProfiles.clearPrimaryIsland(island.ownerUuid()));
            removeMigrationExtraction(islandId);
        });
        if (result.rolledBack()) {
            lastRollbackPlan = null;
            lastApprovalToken = "";
        }
        return "{\"state\":\"" + MigrationRunState.ROLLED_BACK + "\"" + migrationBoundaryFields() + rollbackSafetyFields(true) + ",\"rollbackPlanAvailable\":true,\"rollbackPlan\":" + rollbackPlanJson(plan) + ",\"rolledBack\":" + result.rolledBack() + ",\"removedIslands\":" + result.removedIslands() + ",\"rollbackPlanConsumed\":" + result.rolledBack() + ",\"issues\":" + issuesJson(result.issues()) + "}";
    }

    private String rollbackSafetyFields(boolean planAvailable) {
        return ",\"rollbackScope\":\"last-successful-import-only\""
            + ",\"rollbackPlanRequired\":" + !planAvailable
            + ",\"rollbackTarget\":\"" + (hardRollbackTarget == null ? "mark-deleted-core-records" : "hard-rollback-target") + "\""
            + ",\"rollbackStorageCleanup\":" + (hardRollbackTarget != null)
            + ",\"rollbackWorldBundleCleanup\":true"
            + ",\"rollbackRuntimeDependency\":\"none-superiorskyblock2-input-only\""
            + ",\"rollbackReentryPolicy\":\"consume-plan-after-success\"";
    }

    private void removeMigrationExtraction(java.util.UUID islandId) {
        Path root = lastExtractionRoot == null ? migrationBundleRoot : lastExtractionRoot;
        Path migrationDir = root.resolve("islands").resolve(islandId.toString()).resolve("migration");
        if (!Files.exists(migrationDir)) {
            return;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(migrationDir)) {
            paths.sorted((left, right) -> right.compareTo(left)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (java.io.IOException exception) {
                    throw new IllegalStateException("failed to delete migration extraction " + path, exception);
                }
            });
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("failed to delete migration extraction " + migrationDir, exception);
        }
    }

    private String reportFields(MigrationReport report) {
        return ",\"members\":" + report.members()
            + ",\"memberRoles\":" + report.memberRoles()
            + ",\"bannedVisitors\":" + report.bannedVisitors()
            + ",\"homes\":" + report.homes()
            + ",\"warps\":" + report.warps()
            + ",\"islandLocations\":" + report.islandLocations()
            + ",\"sourceWorlds\":" + report.sourceWorlds()
            + ",\"islandSizes\":" + report.islandSizes()
            + ",\"levels\":" + report.levels()
            + ",\"worthValues\":" + report.worthValues()
            + ",\"biomes\":" + report.biomes()
            + ",\"bankBalances\":" + report.bankBalances()
            + ",\"flags\":" + report.flags()
            + ",\"permissions\":" + report.permissions()
            + ",\"upgrades\":" + report.upgrades()
            + ",\"limits\":" + report.limits()
            + ",\"completedMissions\":" + report.completedMissions()
            + ",\"blockValues\":" + report.blockValues()
            + ",\"blockCounts\":" + report.blockCounts()
            + ",\"manifestGenerated\":" + report.manifestGenerated()
            + ",\"manifestStatus\":\"" + escape(report.manifestStatus()) + "\""
            + ",\"conflictIssues\":" + report.conflictIssues()
            + ",\"conflictStatus\":\"" + escape(report.conflictStatus()) + "\""
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

    private boolean originLocationMatches(MigrationManifest manifest) {
        Optional<kr.lunaf.cloudislands.api.model.IslandHomeSnapshot> origin = metadata.home(manifest.islandId(), "origin");
        if (origin.isEmpty()) {
            return false;
        }
        kr.lunaf.cloudislands.migration.MigrationLocation expected = manifest.islandLocation();
        IslandLocation actual = origin.get().location();
        return actual.worldName().equals(expected.worldName())
            && Double.compare(actual.localX(), expected.x()) == 0
            && Double.compare(actual.localY(), expected.y()) == 0
            && Double.compare(actual.localZ(), expected.z()) == 0
            && Float.compare(actual.yaw(), expected.yaw()) == 0
            && Float.compare(actual.pitch(), expected.pitch()) == 0;
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

    private Path migrationManifestPath() {
        return migrationBundleRoot.resolve("manifests").resolve("superiorskyblock2-last-scan.json");
    }

    private Path migrationReportPath(String stage) {
        return migrationBundleRoot.resolve("reports").resolve("superiorskyblock2-" + stage + "-report.json");
    }

    private Path resolveMigrationBundleRoot(String path) {
        if (path == null || path.isBlank()) {
            return migrationBundleRoot;
        }
        Path requested = Path.of(path);
        return requested.isAbsolute() ? requested : migrationBundleRoot.resolve(requested).normalize();
    }

    private void writeMigrationManifestFile(String sourcePath, Path manifestPath, List<MigrationManifest> manifests) throws java.io.IOException {
        Files.createDirectories(manifestPath.getParent());
        Files.writeString(manifestPath, migrationManifestJson(sourcePath, manifests), StandardCharsets.UTF_8);
    }

    private void writeMigrationReportFile(String state, Path reportPath, MigrationReport report) throws java.io.IOException {
        Files.createDirectories(reportPath.getParent());
        Files.writeString(reportPath, migrationReportJson(state, report), StandardCharsets.UTF_8);
    }

    private String migrationReportJson(String state, MigrationReport report) {
        return "{\"state\":\"" + escape(state) + "\","
            + "\"generatedAt\":\"" + Instant.now() + "\","
            + "\"manifests\":" + report.manifests() + ','
            + "\"members\":" + report.members() + ','
            + "\"memberRoles\":" + report.memberRoles() + ','
            + "\"bannedVisitors\":" + report.bannedVisitors() + ','
            + "\"homes\":" + report.homes() + ','
            + "\"warps\":" + report.warps() + ','
            + "\"islandLocations\":" + report.islandLocations() + ','
            + "\"sourceWorlds\":" + report.sourceWorlds() + ','
            + "\"islandSizes\":" + report.islandSizes() + ','
            + "\"levels\":" + report.levels() + ','
            + "\"worthValues\":" + report.worthValues() + ','
            + "\"biomes\":" + report.biomes() + ','
            + "\"bankBalances\":" + report.bankBalances() + ','
            + "\"flags\":" + report.flags() + ','
            + "\"permissions\":" + report.permissions() + ','
            + "\"upgrades\":" + report.upgrades() + ','
            + "\"limits\":" + report.limits() + ','
            + "\"completedMissions\":" + report.completedMissions() + ','
            + "\"blockValues\":" + report.blockValues() + ','
            + "\"blockCounts\":" + report.blockCounts() + ','
            + "\"manifestGenerated\":" + report.manifestGenerated() + ','
            + "\"manifestStatus\":\"" + escape(report.manifestStatus()) + "\","
            + "\"conflictIssues\":" + report.conflictIssues() + ','
            + "\"conflictStatus\":\"" + escape(report.conflictStatus()) + "\","
            + "\"blockingIssues\":" + report.blockingIssues() + ','
            + "\"warningIssues\":" + report.warningIssues() + ','
            + "\"canImport\":" + report.canImport() + ','
            + "\"issues\":" + issuesJson(report.issues())
            + "}";
    }

    private String migrationManifestJson(String sourcePath, List<MigrationManifest> manifests) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"source\":\"").append(escape(sourcePath)).append("\",")
            .append("\"generatedAt\":\"").append(Instant.now()).append("\",")
            .append("\"manifests\":[");
        for (int index = 0; index < manifests.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            appendManifestJson(builder, manifests.get(index));
        }
        return builder.append("]}").toString();
    }

    private void appendManifestJson(StringBuilder builder, MigrationManifest manifest) {
        builder.append("{\"islandId\":\"").append(manifest.islandId()).append("\",")
            .append("\"ownerUuid\":\"").append(manifest.ownerUuid()).append("\",")
            .append("\"members\":").append(uuidArrayJson(manifest.members())).append(',')
            .append("\"memberRoles\":").append(memberRolesJson(manifest)).append(',')
            .append("\"bannedVisitors\":").append(uuidArrayJson(manifest.bannedVisitors())).append(',')
            .append("\"islandLocation\":").append(locationJson(manifest.islandLocation())).append(',')
            .append("\"homes\":").append(homesJson(manifest)).append(',')
            .append("\"warps\":").append(warpsJson(manifest)).append(',')
            .append("\"flags\":").append(flagsJson(manifest)).append(',')
            .append("\"permissions\":").append(permissionsJson(manifest)).append(',')
            .append("\"upgrades\":").append(upgradesJson(manifest)).append(',')
            .append("\"limits\":").append(limitsJson(manifest)).append(',')
            .append("\"completedMissions\":").append(missionsJson(manifest)).append(',')
            .append("\"blockValues\":").append(blockValuesJson(manifest)).append(',')
            .append("\"blockCounts\":").append(blockCountsJson(manifest)).append(',')
            .append("\"biomeKey\":\"").append(escape(manifest.biomeKey())).append("\",")
            .append("\"bankBalance\":\"").append(escape(manifest.bankBalance())).append("\",")
            .append("\"publicAccess\":").append(manifest.publicAccess()).append(',')
            .append("\"locked\":").append(manifest.locked()).append(',')
            .append("\"size\":").append(manifest.size()).append(',')
            .append("\"level\":").append(manifest.level()).append(',')
            .append("\"worth\":\"").append(escape(manifest.worth())).append("\",")
            .append("\"sourceWorldPath\":\"").append(escape(manifest.sourceWorldPath())).append("\"}");
    }

    private String uuidArrayJson(List<java.util.UUID> uuids) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < uuids.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append('"').append(uuids.get(index)).append('"');
        }
        return builder.append(']').toString();
    }

    private String memberRolesJson(MigrationManifest manifest) {
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (kr.lunaf.cloudislands.migration.MigrationMemberRole role : manifest.memberRoles()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append("{\"playerUuid\":\"").append(role.playerUuid()).append("\",\"roleName\":\"").append(escape(role.roleName())).append("\"}");
        }
        return builder.append(']').toString();
    }

    private String locationJson(kr.lunaf.cloudislands.migration.MigrationLocation location) {
        return "{\"present\":" + location.present()
            + ",\"worldName\":\"" + escape(location.worldName())
            + "\",\"x\":" + location.x()
            + ",\"y\":" + location.y()
            + ",\"z\":" + location.z()
            + ",\"yaw\":" + location.yaw()
            + ",\"pitch\":" + location.pitch()
            + "}";
    }

    private String homesJson(MigrationManifest manifest) {
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (kr.lunaf.cloudislands.migration.MigrationHome home : manifest.homes()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append("{\"name\":\"").append(escape(home.name())).append("\",\"worldName\":\"").append(escape(home.worldName()))
                .append("\",\"x\":").append(home.x()).append(",\"y\":").append(home.y()).append(",\"z\":").append(home.z())
                .append(",\"yaw\":").append(home.yaw()).append(",\"pitch\":").append(home.pitch()).append('}');
        }
        return builder.append(']').toString();
    }

    private String warpsJson(MigrationManifest manifest) {
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (kr.lunaf.cloudislands.migration.MigrationWarp warp : manifest.warps()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append("{\"name\":\"").append(escape(warp.name())).append("\",\"worldName\":\"").append(escape(warp.worldName()))
                .append("\",\"x\":").append(warp.x()).append(",\"y\":").append(warp.y()).append(",\"z\":").append(warp.z())
                .append(",\"yaw\":").append(warp.yaw()).append(",\"pitch\":").append(warp.pitch())
                .append(",\"publicAccess\":").append(warp.publicAccess()).append('}');
        }
        return builder.append(']').toString();
    }

    private String flagsJson(MigrationManifest manifest) {
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (kr.lunaf.cloudislands.migration.MigrationFlag flag : manifest.flags()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append("{\"flagName\":\"").append(escape(flag.flagName())).append("\",\"value\":\"").append(escape(flag.value())).append("\"}");
        }
        return builder.append(']').toString();
    }

    private String permissionsJson(MigrationManifest manifest) {
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (kr.lunaf.cloudislands.migration.MigrationPermission permission : manifest.permissions()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append("{\"roleName\":\"").append(escape(permission.roleName())).append("\",\"permissionName\":\"").append(escape(permission.permissionName()))
                .append("\",\"allowed\":").append(permission.allowed()).append('}');
        }
        return builder.append(']').toString();
    }

    private String upgradesJson(MigrationManifest manifest) {
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (kr.lunaf.cloudislands.migration.MigrationUpgrade upgrade : manifest.upgrades()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append("{\"upgradeKey\":\"").append(escape(upgrade.upgradeKey())).append("\",\"level\":").append(upgrade.level()).append('}');
        }
        return builder.append(']').toString();
    }

    private String limitsJson(MigrationManifest manifest) {
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (kr.lunaf.cloudislands.migration.MigrationLimit limit : manifest.limits()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append("{\"limitKey\":\"").append(escape(limit.limitKey())).append("\",\"value\":").append(limit.value()).append('}');
        }
        return builder.append(']').toString();
    }

    private String missionsJson(MigrationManifest manifest) {
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (kr.lunaf.cloudislands.migration.MigrationMission mission : manifest.completedMissions()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append("{\"missionKey\":\"").append(escape(mission.missionKey())).append("\",\"kind\":\"").append(escape(mission.kind())).append("\"}");
        }
        return builder.append(']').toString();
    }

    private String blockValuesJson(MigrationManifest manifest) {
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (kr.lunaf.cloudislands.migration.MigrationBlockValue value : manifest.blockValues()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append("{\"materialKey\":\"").append(escape(value.materialKey())).append("\",\"worth\":\"").append(escape(value.worth()))
                .append("\",\"levelPoints\":").append(value.levelPoints()).append(",\"limit\":").append(value.limit()).append('}');
        }
        return builder.append(']').toString();
    }

    private String blockCountsJson(MigrationManifest manifest) {
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (kr.lunaf.cloudislands.migration.MigrationBlockCount count : manifest.blockCounts()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append("{\"materialKey\":\"").append(escape(count.materialKey())).append("\",\"count\":").append(count.count()).append('}');
        }
        return builder.append(']').toString();
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
