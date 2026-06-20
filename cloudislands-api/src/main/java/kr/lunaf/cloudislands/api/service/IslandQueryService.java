package kr.lunaf.cloudislands.api.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.BlockValueSnapshot;
import kr.lunaf.cloudislands.api.model.IslandBankSnapshot;
import kr.lunaf.cloudislands.api.model.IslandBanSnapshot;
import kr.lunaf.cloudislands.api.model.IslandBoundarySnapshot;
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
import kr.lunaf.cloudislands.api.model.IslandRankSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRegionSnapshot;
import kr.lunaf.cloudislands.api.model.IslandReviewRankSnapshot;
import kr.lunaf.cloudislands.api.model.IslandReviewSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRoleSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandSizeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.model.IslandSnapshotRecord;
import kr.lunaf.cloudislands.api.model.IslandVisitorStatsSnapshot;
import kr.lunaf.cloudislands.api.model.IslandWarehouseItemSnapshot;
import kr.lunaf.cloudislands.api.model.IslandWarpSnapshot;
import kr.lunaf.cloudislands.api.model.IslandWorthSnapshot;
import kr.lunaf.cloudislands.api.upgrade.IslandUpgradeSnapshot;
import kr.lunaf.cloudislands.api.upgrade.UpgradeRuleSnapshot;

public interface IslandQueryService {
    CompletableFuture<Optional<IslandSnapshot>> getIsland(UUID islandId);
    CompletableFuture<Optional<IslandSnapshot>> getIslandByOwner(UUID ownerUuid);
    CompletableFuture<Optional<IslandSnapshot>> getIslandAt(String worldName, int blockX, int blockY, int blockZ);
    CompletableFuture<Optional<IslandRegionSnapshot>> getRegion(UUID islandId);
    CompletableFuture<List<IslandMemberSnapshot>> getMembers(UUID islandId);
    CompletableFuture<List<IslandBanSnapshot>> getBans(UUID islandId);
    CompletableFuture<List<IslandInviteSnapshot>> getPendingInvites(UUID playerUuid);
    CompletableFuture<List<IslandHomeSnapshot>> getHomes(UUID islandId);
    CompletableFuture<List<IslandWarpSnapshot>> getWarps(UUID islandId);
    CompletableFuture<List<IslandWarpSnapshot>> getPublicWarps(int limit);
    default CompletableFuture<List<IslandWarpSnapshot>> getPublicWarps(int limit, String category, String query) {
        return getPublicWarps(limit).thenApply(warps -> {
            String normalizedCategory = IslandWarpSnapshot.normalizeCategory(category);
            String normalizedQuery = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
            return warps.stream()
                .filter(warp -> category == null || category.isBlank() || warp.category().equalsIgnoreCase(normalizedCategory))
                .filter(warp -> normalizedQuery.isBlank() || warp.name().toLowerCase(java.util.Locale.ROOT).contains(normalizedQuery) || warp.category().toLowerCase(java.util.Locale.ROOT).contains(normalizedQuery))
                .toList();
        });
    }
    CompletableFuture<IslandFlagsSnapshot> getFlags(UUID islandId);
    CompletableFuture<IslandBiomeSnapshot> getBiome(UUID islandId);
    CompletableFuture<List<IslandLimitSnapshot>> getLimits(UUID islandId);
    CompletableFuture<List<IslandPermissionRuleSnapshot>> getPermissionRules(UUID islandId);
    CompletableFuture<List<IslandRoleSnapshot>> getRoles(UUID islandId);
    CompletableFuture<IslandBoundarySnapshot> getBoundary(UUID islandId);
    CompletableFuture<IslandSizeSnapshot> getSize(UUID islandId);
    CompletableFuture<IslandWorthSnapshot> getWorth(UUID islandId);
    CompletableFuture<IslandLevelSnapshot> getLevel(UUID islandId);
    CompletableFuture<List<IslandRankSnapshot>> getTopByLevel(int limit);
    CompletableFuture<List<IslandRankSnapshot>> getTopByWorth(int limit);
    CompletableFuture<List<IslandReviewRankSnapshot>> getTopByReviews(int limit);
    default CompletableFuture<List<IslandReviewSnapshot>> getReviews(UUID islandId, int limit) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Island review queries require CloudIslands API 1.1.0 or newer"));
    }
    default CompletableFuture<IslandVisitorStatsSnapshot> getVisitorStats(UUID islandId, int limit) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Island visitor stats queries require CloudIslands API 1.1.0 or newer"));
    }
    default CompletableFuture<List<IslandWarehouseItemSnapshot>> getWarehouse(UUID islandId, int limit) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Island warehouse queries require CloudIslands API 1.1.0 or newer"));
    }
    CompletableFuture<List<IslandSnapshot>> getPublicIslands(int limit);
    CompletableFuture<IslandRuntimeSnapshot> getRuntime(UUID islandId);
    CompletableFuture<List<BlockValueSnapshot>> getBlockValues();
    CompletableFuture<List<UpgradeRuleSnapshot>> getUpgradeRules();
    CompletableFuture<List<IslandUpgradeSnapshot>> getUpgrades(UUID islandId);
    CompletableFuture<List<IslandMissionSnapshot>> getMissions(UUID islandId, String kind);
    CompletableFuture<List<IslandSnapshotRecord>> getSnapshots(UUID islandId, int limit);
    CompletableFuture<List<IslandLogRecord>> getLogs(UUID islandId, int limit);
    CompletableFuture<IslandBankSnapshot> getBank(UUID islandId);
}
