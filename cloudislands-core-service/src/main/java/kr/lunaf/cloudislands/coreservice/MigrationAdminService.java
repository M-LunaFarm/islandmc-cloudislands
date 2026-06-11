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
import kr.lunaf.cloudislands.coreservice.repository.IslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;
import kr.lunaf.cloudislands.coreservice.upgrade.IslandUpgradeRepository;
import kr.lunaf.cloudislands.coreservice.upgrade.UpgradePolicy;
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
    private final IslandPermissionRuleRepository permissionRules;
    private final IslandUpgradeRepository upgrades;
    private final IslandBankRepository bank;
    private final IslandLimitRepository limits;
    private final IslandMissionRepository missions;
    private final SuperiorSkyblock2MigrationScanner scanner = new SuperiorSkyblock2MigrationScanner();
    private final CloudIslandsMigrationImporter importer = new CloudIslandsMigrationImporter();
    private final MigrationVerifier verifier = new MigrationVerifier();
    private final MigrationRollbackService rollback = new MigrationRollbackService();
    private SuperiorSkyblock2MigrationScanner.ScanResult lastScan = new SuperiorSkyblock2MigrationScanner.ScanResult(List.of(), List.of());
    private MigrationImportPlan lastPlan = new MigrationImportPlan(List.of(), List.of());
    private MigrationRollbackPlan lastRollbackPlan;

    public MigrationAdminService(IslandRepository islands, IslandMetadataRepository metadata, PlayerProfileRepository playerProfiles, IslandPermissionRuleRepository permissionRules, IslandUpgradeRepository upgrades, IslandBankRepository bank, IslandLimitRepository limits, IslandMissionRepository missions) {
        this.islands = islands;
        this.metadata = metadata;
        this.playerProfiles = playerProfiles;
        this.permissionRules = permissionRules;
        this.upgrades = upgrades;
        this.bank = bank;
        this.limits = limits;
        this.missions = missions;
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
                missions.complete(manifest.islandId(), manifest.ownerUuid(), mission.missionKey());
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
        return "{\"state\":\"" + state + "\",\"imported\":" + result.imported() + ",\"importedIslands\":" + result.importedIslands() + ",\"issues\":" + issuesJson(result.issues()) + ",\"rollbackPlan\":" + rollbackPlanJson(result.rollbackPlan()) + "}";
    }

    public synchronized String verify() {
        List<MigrationManifest> imported = new ArrayList<>();
        for (MigrationManifest manifest : lastScan.manifests()) {
            islands.findById(manifest.islandId())
                .filter(island -> island.ownerUuid().equals(manifest.ownerUuid()))
                .filter(island -> island.size() == manifest.size())
                .filter(island -> island.level() == manifest.level())
                .filter(island -> island.worth().equals(manifest.worth()))
                .filter(_island -> manifest.members().stream().allMatch(memberUuid -> metadata.isMember(manifest.islandId(), memberUuid)))
                .filter(_island -> memberRolesMatch(manifest))
                .filter(_island -> manifest.bannedVisitors().stream().allMatch(bannedUuid -> metadata.isBanned(manifest.islandId(), bannedUuid)))
                .filter(_island -> manifest.homes().stream().allMatch(home -> metadata.home(manifest.islandId(), home.name()).isPresent()))
                .filter(_island -> manifest.warps().stream().allMatch(warp -> metadata.warp(manifest.islandId(), warp.name()).isPresent()))
                .filter(_island -> manifest.flags().stream().allMatch(flag -> flag.value().equals(metadata.flags(manifest.islandId()).values().get(IslandFlag.valueOf(flag.flagName())))))
                .filter(_island -> permissionsMatch(manifest))
                .filter(_island -> upgradesMatch(manifest))
                .filter(_island -> limitsMatch(manifest))
                .filter(_island -> missionsMatch(manifest))
                .filter(_island -> manifest.biomeKey().isBlank() || metadata.biome(manifest.islandId()).biomeKey().equals(manifest.biomeKey()))
                .filter(_island -> decimal(bank.balance(manifest.islandId()).balance()).compareTo(decimal(manifest.bankBalance())) == 0)
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
