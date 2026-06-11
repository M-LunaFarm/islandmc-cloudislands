package kr.lunaf.cloudislands.api.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandBankSnapshot;
import kr.lunaf.cloudislands.api.model.IslandBanSnapshot;
import kr.lunaf.cloudislands.api.model.IslandBiomeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandFlagsSnapshot;
import kr.lunaf.cloudislands.api.model.IslandHomeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandInviteSnapshot;
import kr.lunaf.cloudislands.api.model.IslandLevelSnapshot;
import kr.lunaf.cloudislands.api.model.IslandLimitSnapshot;
import kr.lunaf.cloudislands.api.model.IslandLogRecord;
import kr.lunaf.cloudislands.api.model.IslandMemberSnapshot;
import kr.lunaf.cloudislands.api.model.IslandMissionSnapshot;
import kr.lunaf.cloudislands.api.model.IslandPermissionRuleSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.model.IslandSnapshotRecord;
import kr.lunaf.cloudislands.api.model.IslandWarpSnapshot;
import kr.lunaf.cloudislands.api.upgrade.IslandUpgradeSnapshot;

public interface IslandQueryService {
    CompletableFuture<Optional<IslandSnapshot>> getIsland(UUID islandId);
    CompletableFuture<Optional<IslandSnapshot>> getIslandByOwner(UUID ownerUuid);
    CompletableFuture<List<IslandMemberSnapshot>> getMembers(UUID islandId);
    CompletableFuture<List<IslandBanSnapshot>> getBans(UUID islandId);
    CompletableFuture<List<IslandInviteSnapshot>> getPendingInvites(UUID playerUuid);
    CompletableFuture<List<IslandHomeSnapshot>> getHomes(UUID islandId);
    CompletableFuture<List<IslandWarpSnapshot>> getWarps(UUID islandId);
    CompletableFuture<IslandFlagsSnapshot> getFlags(UUID islandId);
    CompletableFuture<IslandBiomeSnapshot> getBiome(UUID islandId);
    CompletableFuture<List<IslandLimitSnapshot>> getLimits(UUID islandId);
    CompletableFuture<List<IslandPermissionRuleSnapshot>> getPermissionRules(UUID islandId);
    CompletableFuture<IslandLevelSnapshot> getLevel(UUID islandId);
    CompletableFuture<IslandRuntimeSnapshot> getRuntime(UUID islandId);
    CompletableFuture<List<IslandUpgradeSnapshot>> getUpgrades(UUID islandId);
    CompletableFuture<List<IslandMissionSnapshot>> getMissions(UUID islandId, String kind);
    CompletableFuture<List<IslandSnapshotRecord>> getSnapshots(UUID islandId, int limit);
    CompletableFuture<List<IslandLogRecord>> getLogs(UUID islandId, int limit);
    CompletableFuture<IslandBankSnapshot> getBank(UUID islandId);
}
