package kr.lunaf.cloudislands.api.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandVisitorStatsSnapshot;
import org.junit.jupiter.api.Test;

class IslandQueryServiceSurfaceTest {
    @Test
    void exposesFirstClassProductShellQueries() {
        assertDoesNotThrow(() -> IslandQueryService.class.getMethod("getReviews", UUID.class, int.class));
        assertDoesNotThrow(() -> IslandQueryService.class.getMethod("getVisitorStats", UUID.class, int.class));
        assertDoesNotThrow(() -> IslandQueryService.class.getMethod("getWarehouse", UUID.class, int.class));
    }

    @Test
    void newQueryMethodsAreSafeBinaryCompatibleDefaults() {
        IslandQueryService service = new MinimalQueryService();
        UUID islandId = UUID.randomUUID();

        assertEquals(List.of(), service.getReviews(islandId, 10).join());
        assertEquals(List.of(), service.getWarehouse(islandId, 10).join());
        IslandVisitorStatsSnapshot stats = service.getVisitorStats(islandId, 10).join();
        assertEquals(islandId, stats.islandId());
        assertEquals(0L, stats.totalVisits());
        assertEquals(0L, stats.uniqueVisitors());
        assertEquals(List.of(), stats.recentVisitors());
    }

    @Test
    void visitorStatsSnapshotNormalizesCountsAndRecentVisitors() {
        IslandVisitorStatsSnapshot snapshot = new IslandVisitorStatsSnapshot(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            -1L,
            -2L,
            List.of(new IslandVisitorStatsSnapshot.RecentVisitor(null, null))
        );

        assertEquals(0L, snapshot.totalVisits());
        assertEquals(0L, snapshot.uniqueVisitors());
        assertEquals("", snapshot.recentVisitors().get(0).visitorUuid());
        assertEquals(Instant.EPOCH, snapshot.recentVisitors().get(0).lastVisitedAt());
    }

    private static final class MinimalQueryService implements IslandQueryService {
        @Override public java.util.concurrent.CompletableFuture<java.util.Optional<kr.lunaf.cloudislands.api.model.IslandSnapshot>> getIsland(UUID islandId) { return java.util.concurrent.CompletableFuture.completedFuture(java.util.Optional.empty()); }
        @Override public java.util.concurrent.CompletableFuture<java.util.Optional<kr.lunaf.cloudislands.api.model.IslandSnapshot>> getIslandByOwner(UUID ownerUuid) { return java.util.concurrent.CompletableFuture.completedFuture(java.util.Optional.empty()); }
        @Override public java.util.concurrent.CompletableFuture<java.util.Optional<kr.lunaf.cloudislands.api.model.IslandSnapshot>> getIslandAt(String worldName, int blockX, int blockY, int blockZ) { return java.util.concurrent.CompletableFuture.completedFuture(java.util.Optional.empty()); }
        @Override public java.util.concurrent.CompletableFuture<java.util.Optional<kr.lunaf.cloudislands.api.model.IslandRegionSnapshot>> getRegion(UUID islandId) { return java.util.concurrent.CompletableFuture.completedFuture(java.util.Optional.empty()); }
        @Override public java.util.concurrent.CompletableFuture<List<kr.lunaf.cloudislands.api.model.IslandMemberSnapshot>> getMembers(UUID islandId) { return java.util.concurrent.CompletableFuture.completedFuture(List.of()); }
        @Override public java.util.concurrent.CompletableFuture<List<kr.lunaf.cloudislands.api.model.IslandBanSnapshot>> getBans(UUID islandId) { return java.util.concurrent.CompletableFuture.completedFuture(List.of()); }
        @Override public java.util.concurrent.CompletableFuture<List<kr.lunaf.cloudislands.api.model.IslandInviteSnapshot>> getPendingInvites(UUID playerUuid) { return java.util.concurrent.CompletableFuture.completedFuture(List.of()); }
        @Override public java.util.concurrent.CompletableFuture<List<kr.lunaf.cloudislands.api.model.IslandHomeSnapshot>> getHomes(UUID islandId) { return java.util.concurrent.CompletableFuture.completedFuture(List.of()); }
        @Override public java.util.concurrent.CompletableFuture<List<kr.lunaf.cloudislands.api.model.IslandWarpSnapshot>> getWarps(UUID islandId) { return java.util.concurrent.CompletableFuture.completedFuture(List.of()); }
        @Override public java.util.concurrent.CompletableFuture<List<kr.lunaf.cloudislands.api.model.IslandWarpSnapshot>> getPublicWarps(int limit) { return java.util.concurrent.CompletableFuture.completedFuture(List.of()); }
        @Override public java.util.concurrent.CompletableFuture<kr.lunaf.cloudislands.api.model.IslandFlagsSnapshot> getFlags(UUID islandId) { return null; }
        @Override public java.util.concurrent.CompletableFuture<kr.lunaf.cloudislands.api.model.IslandBiomeSnapshot> getBiome(UUID islandId) { return null; }
        @Override public java.util.concurrent.CompletableFuture<List<kr.lunaf.cloudislands.api.model.IslandLimitSnapshot>> getLimits(UUID islandId) { return java.util.concurrent.CompletableFuture.completedFuture(List.of()); }
        @Override public java.util.concurrent.CompletableFuture<List<kr.lunaf.cloudislands.api.model.IslandPermissionRuleSnapshot>> getPermissionRules(UUID islandId) { return java.util.concurrent.CompletableFuture.completedFuture(List.of()); }
        @Override public java.util.concurrent.CompletableFuture<List<kr.lunaf.cloudislands.api.model.IslandRoleSnapshot>> getRoles(UUID islandId) { return java.util.concurrent.CompletableFuture.completedFuture(List.of()); }
        @Override public java.util.concurrent.CompletableFuture<kr.lunaf.cloudislands.api.model.IslandBoundarySnapshot> getBoundary(UUID islandId) { return null; }
        @Override public java.util.concurrent.CompletableFuture<kr.lunaf.cloudislands.api.model.IslandSizeSnapshot> getSize(UUID islandId) { return null; }
        @Override public java.util.concurrent.CompletableFuture<kr.lunaf.cloudislands.api.model.IslandWorthSnapshot> getWorth(UUID islandId) { return null; }
        @Override public java.util.concurrent.CompletableFuture<kr.lunaf.cloudislands.api.model.IslandLevelSnapshot> getLevel(UUID islandId) { return null; }
        @Override public java.util.concurrent.CompletableFuture<List<kr.lunaf.cloudislands.api.model.IslandRankSnapshot>> getTopByLevel(int limit) { return java.util.concurrent.CompletableFuture.completedFuture(List.of()); }
        @Override public java.util.concurrent.CompletableFuture<List<kr.lunaf.cloudislands.api.model.IslandRankSnapshot>> getTopByWorth(int limit) { return java.util.concurrent.CompletableFuture.completedFuture(List.of()); }
        @Override public java.util.concurrent.CompletableFuture<List<kr.lunaf.cloudislands.api.model.IslandReviewRankSnapshot>> getTopByReviews(int limit) { return java.util.concurrent.CompletableFuture.completedFuture(List.of()); }
        @Override public java.util.concurrent.CompletableFuture<List<kr.lunaf.cloudislands.api.model.IslandSnapshot>> getPublicIslands(int limit) { return java.util.concurrent.CompletableFuture.completedFuture(List.of()); }
        @Override public java.util.concurrent.CompletableFuture<kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot> getRuntime(UUID islandId) { return null; }
        @Override public java.util.concurrent.CompletableFuture<List<kr.lunaf.cloudislands.api.model.BlockValueSnapshot>> getBlockValues() { return java.util.concurrent.CompletableFuture.completedFuture(List.of()); }
        @Override public java.util.concurrent.CompletableFuture<List<kr.lunaf.cloudislands.api.upgrade.UpgradeRuleSnapshot>> getUpgradeRules() { return java.util.concurrent.CompletableFuture.completedFuture(List.of()); }
        @Override public java.util.concurrent.CompletableFuture<List<kr.lunaf.cloudislands.api.upgrade.IslandUpgradeSnapshot>> getUpgrades(UUID islandId) { return java.util.concurrent.CompletableFuture.completedFuture(List.of()); }
        @Override public java.util.concurrent.CompletableFuture<List<kr.lunaf.cloudislands.api.model.IslandMissionSnapshot>> getMissions(UUID islandId, String kind) { return java.util.concurrent.CompletableFuture.completedFuture(List.of()); }
        @Override public java.util.concurrent.CompletableFuture<List<kr.lunaf.cloudislands.api.model.IslandSnapshotRecord>> getSnapshots(UUID islandId, int limit) { return java.util.concurrent.CompletableFuture.completedFuture(List.of()); }
        @Override public java.util.concurrent.CompletableFuture<List<kr.lunaf.cloudislands.api.model.IslandLogRecord>> getLogs(UUID islandId, int limit) { return java.util.concurrent.CompletableFuture.completedFuture(List.of()); }
        @Override public java.util.concurrent.CompletableFuture<kr.lunaf.cloudislands.api.model.IslandBankSnapshot> getBank(UUID islandId) { return null; }
    }
}
