package kr.lunaf.cloudislands.coreservice;

import java.nio.file.Path;
import kr.lunaf.cloudislands.coreservice.bank.IslandBankRepository;
import kr.lunaf.cloudislands.coreservice.limit.IslandLimitRepository;
import kr.lunaf.cloudislands.coreservice.mission.IslandMissionRepository;
import kr.lunaf.cloudislands.coreservice.permission.IslandPermissionRuleRepository;
import kr.lunaf.cloudislands.coreservice.profile.PlayerProfileRepository;
import kr.lunaf.cloudislands.coreservice.ranking.IslandLevelRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.snapshot.IslandSnapshotRepository;
import kr.lunaf.cloudislands.coreservice.upgrade.IslandUpgradeRepository;
import kr.lunaf.cloudislands.coreservice.warehouse.IslandWarehouseRepository;
import kr.lunaf.cloudislands.coreservice.workflow.IslandLifecycleWorkflow;
import kr.lunaf.cloudislands.migration.rollback.MigrationRollbackService.RollbackTarget;

public final class MigrationAdminService {
    static final String MIGRATION_SNAPSHOT_REASON = MigrationAdminBackend.MIGRATION_SNAPSHOT_REASON;
    static final String MIGRATION_TARGET_FIELDS = MigrationAdminBackend.MIGRATION_TARGET_FIELDS;
    static final String MIGRATION_PIPELINE_STEPS = MigrationAdminBackend.MIGRATION_PIPELINE_STEPS;
    static final String MIGRATION_COMMAND_SET = MigrationAdminBackend.MIGRATION_COMMAND_SET;

    private final MigrationAdminBackend backend;

    public MigrationAdminService(IslandRepository islands, IslandMetadataRepository metadata, PlayerProfileRepository playerProfiles, IslandPermissionRuleRepository permissionRules, IslandUpgradeRepository upgrades, IslandBankRepository bank, IslandLimitRepository limits, IslandMissionRepository missions, IslandLevelRepository levels, IslandSnapshotRepository snapshots, RollbackTarget hardRollbackTarget) {
        this.backend = new MigrationAdminBackend(islands, metadata, playerProfiles, permissionRules, upgrades, bank, limits, missions, levels, snapshots, hardRollbackTarget);
    }

    public MigrationAdminService(IslandRepository islands, IslandMetadataRepository metadata, PlayerProfileRepository playerProfiles, IslandPermissionRuleRepository permissionRules, IslandUpgradeRepository upgrades, IslandBankRepository bank, IslandLimitRepository limits, IslandMissionRepository missions, IslandLevelRepository levels, IslandSnapshotRepository snapshots, RollbackTarget hardRollbackTarget, Path migrationBundleRoot) {
        this.backend = new MigrationAdminBackend(islands, metadata, playerProfiles, permissionRules, upgrades, bank, limits, missions, levels, snapshots, hardRollbackTarget, migrationBundleRoot);
    }

    public MigrationAdminService(IslandRepository islands, IslandMetadataRepository metadata, PlayerProfileRepository playerProfiles, IslandPermissionRuleRepository permissionRules, IslandUpgradeRepository upgrades, IslandBankRepository bank, IslandLimitRepository limits, IslandMissionRepository missions, IslandLevelRepository levels, IslandSnapshotRepository snapshots, RollbackTarget hardRollbackTarget, Path migrationBundleRoot, IslandLifecycleWorkflow activationTester) {
        this.backend = new MigrationAdminBackend(islands, metadata, playerProfiles, permissionRules, upgrades, bank, limits, missions, levels, snapshots, hardRollbackTarget, migrationBundleRoot, activationTester);
    }

    public MigrationAdminService(IslandRepository islands, IslandMetadataRepository metadata, PlayerProfileRepository playerProfiles, IslandPermissionRuleRepository permissionRules, IslandUpgradeRepository upgrades, IslandBankRepository bank, IslandLimitRepository limits, IslandMissionRepository missions, IslandLevelRepository levels, IslandSnapshotRepository snapshots, RollbackTarget hardRollbackTarget, IslandRuntimeRepository runtimes, Path migrationBundleRoot, IslandLifecycleWorkflow activationTester) {
        this.backend = new MigrationAdminBackend(islands, metadata, playerProfiles, permissionRules, upgrades, bank, limits, missions, levels, snapshots, hardRollbackTarget, runtimes, migrationBundleRoot, activationTester);
    }

    public MigrationAdminService(IslandRepository islands, IslandMetadataRepository metadata, PlayerProfileRepository playerProfiles, IslandPermissionRuleRepository permissionRules, IslandUpgradeRepository upgrades, IslandBankRepository bank, IslandLimitRepository limits, IslandMissionRepository missions, IslandLevelRepository levels, IslandWarehouseRepository warehouse, IslandSnapshotRepository snapshots, RollbackTarget hardRollbackTarget, IslandRuntimeRepository runtimes, Path migrationBundleRoot, IslandLifecycleWorkflow activationTester) {
        this.backend = new MigrationAdminBackend(islands, metadata, playerProfiles, permissionRules, upgrades, bank, limits, missions, levels, warehouse, snapshots, hardRollbackTarget, runtimes, migrationBundleRoot, activationTester);
    }

    public synchronized String scan(String path) {
        return backend.scan(path);
    }

    public synchronized String dryRun() {
        return backend.dryRun();
    }

    public synchronized String status() {
        return backend.status();
    }

    public synchronized String extractWorldBundles(String outputPath) {
        return backend.extractWorldBundles(outputPath);
    }

    public synchronized String importLastPlan() {
        return backend.importLastPlan();
    }

    public synchronized String importLastPlan(String approvalToken) {
        return backend.importLastPlan(approvalToken);
    }

    public synchronized String verify() {
        return backend.verify();
    }

    public synchronized String verify(String bundleRootPath) {
        return backend.verify(bundleRootPath);
    }

    public synchronized String rollbackLastImport() {
        return backend.rollbackLastImport();
    }
}
